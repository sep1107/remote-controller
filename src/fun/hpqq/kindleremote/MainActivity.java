package fun.hpqq.kindleremote;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.util.Log;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final InputGate gate = new InputGate();
    private final Map<String, Integer> held = new HashMap<>();
    private final Map<String, String> axisHeld = new HashMap<>();
    private final Map<String, Integer> bindings = new HashMap<>();
    // No unbounded backlog: slow or lost Wi-Fi must not queue a chapter of turns.
    private final ThreadPoolExecutor network = new ThreadPoolExecutor(1, 1, 0,
        TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
    private SharedPreferences prefs;
    private EditText host;
    private TextView status, input;
    private LinearLayout root;
    private Switch capture, musicMode;
    private MediaSession mediaSession;
    private final Set<String> sleepHeld = new HashSet<>();
    private final Button[] bindingButtons = new Button[5];
    private int learning = -1;
    private boolean active, dark;
    private volatile long session;
    private static final int[] AXES = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
        MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
        MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS};

    private final Runnable poll = new Runnable() {
        public void run() {
            if (!active) return;
            for (Map.Entry<String, Integer> e : held.entrySet())
                if (gate.press(e.getKey(), e.getValue(), SystemClock.uptimeMillis())) send(e.getValue());
            handler.postDelayed(this, 100);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("remote", MODE_PRIVATE);
        defaults();
        if (prefs.getBoolean("custom", false)) bindings.clear();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet())
            if (e.getKey().startsWith("bind:") && e.getValue() instanceof Integer)
                bindings.put(e.getKey().substring(5), (Integer) e.getValue());
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 32);
        root.setBackgroundColor(Color.rgb(18, 22, 28));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        TextView title = text("远程控制器", 28);
        title.setPadding(0, 12, 0, 6);
        text("翻页、亮度与息屏", 14);
        host = field("Kindle IP", prefs.getString("host", ""));
        host.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        button(root, "测试连接并保存", v -> {
            root.requestFocus();
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(host.getWindowToken(), 0);
            submit(-1);
        });
        status = text("启动 Kindle 接收服务，再测试连接", 15);
        input = text("最近输入：等待按键", 14);
        capture = new Switch(this);
        capture.setText("接收遥控按键（App 保持前台）");
        capture.setTextColor(Color.WHITE);
        root.addView(capture);
        capture.setOnCheckedChangeListener((b, checked) -> {
            clearInput();
            updateMediaSession();
            if (checked) {
                host.clearFocus(); root.requestFocus();
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                status.setText("按键接收已开启；休眠键需按住 1 秒");
            } else {
                learning = -1;
                session++; network.getQueue().clear();
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                status.setText("按键接收已停止");
            }
        });
        musicMode = new Switch(this);
        musicMode.setText("M 档音乐遥控：音量调亮度，播放键点按息屏");
        musicMode.setTextColor(Color.WHITE);
        musicMode.setChecked(prefs.getBoolean("musicMode", true));
        root.addView(musicMode);
        musicMode.setOnCheckedChangeListener((b, checked) -> {
            clearInput();
            prefs.edit().putBoolean("musicMode", checked).apply();
            updateMediaSession();
        });
        mediaSession = new MediaSession(this, "KindleRemote");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
            | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                return event != null && handleKey(event);
            }
        }, handler);
        mediaSession.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(PlaybackState.STATE_PAUSED, 0, 0).build());
        LinearLayout pages = row();
        button(pages, "← 上一页", v -> send(0));
        button(pages, "下一页 →", v -> send(1));
        LinearLayout lights = row();
        button(lights, "亮度 −", v -> send(3));
        button(lights, "亮度 +", v -> send(2));
        button(root, "让 Kindle 休眠", v -> send(4));
        button(root, "暗屏阅读模式", v -> {
            dark = !dark;
            WindowManager.LayoutParams p = getWindow().getAttributes();
            p.screenBrightness = dark ? 0.01f : -1f;
            getWindow().setAttributes(p);
            root.setBackgroundColor(dark ? Color.BLACK : Color.rgb(18, 22, 28));
            ((Button)v).setText(dark ? "恢复屏幕亮度" : "暗屏阅读模式");
        });
        text("按键学习", 22);
        text("点击操作，再按翻页器／手柄上的键或推动摇杆。长按列表按钮可解除该操作的所有绑定。", 14);
        for (int i = 0; i < 5; i++) {
            final int action = i;
            bindingButtons[i] = button(root, "", v -> {
                capture.setChecked(true);
                clearInput();
                learning = action;
                status.setText("等待绑定：" + RemoteClient.LABELS[action] + "；再点同一按钮重新学习");
            });
            bindingButtons[i].setOnLongClickListener(v -> {
                learning = -1; clearInput();
                bindings.entrySet().removeIf(e -> e.getValue() == action);
                saveBindings(); refreshBindings();
                status.setText("已解除：" + RemoteClient.LABELS[action]);
                return true;
            });
        }
        refreshBindings();
        button(root, "恢复默认绑定", v -> {
            learning = -1; clearInput(); defaults(); saveBindings(); refreshBindings();
        });
        text("M 档音乐遥控：上一曲／下一曲翻页，音量＋／−调亮度，播放／暂停点按息屏。关闭此模式恢复音量翻页。", 14);
        text("默认：音量+/右/A/R1 下一页；音量−/左/B/L1 上一页；上/下 调亮度；Start 长按休眠。摇杆与十字方向轴同样支持。", 14);
        text("先在安卓系统配对蓝牙设备。仅处理系统送到 App 的输入；模拟触摸的戒指、被系统截获的键可能无法学习。切换 App 或锁住手机会停止接收。", 14);
        root.setFocusableInTouchMode(true);
    }

    private TextView text(String s, int size) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(Color.rgb(220, 226, 235)); t.setPadding(0, 10, 0, 10); root.addView(t); return t;
    }
    private EditText field(String hint, String value) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setText(value);
        e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); root.addView(e); return e;
    }
    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this); root.addView(r); return r;
    }
    private Button button(LinearLayout parent, String label, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(listener);
        parent.addView(b, parent == root ? new LinearLayout.LayoutParams(-1, -2)
            : new LinearLayout.LayoutParams(0, -2, 1));
        return b;
    }
    private void defaults() {
        bindings.clear();
        bindings.put("key:" + KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0);
        bindings.put("key:" + KeyEvent.KEYCODE_MEDIA_NEXT, 1);
        int[][] keys = {{KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_L1},
            {KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_SPACE},
            {KeyEvent.KEYCODE_DPAD_UP}, {KeyEvent.KEYCODE_DPAD_DOWN}, {KeyEvent.KEYCODE_BUTTON_START}};
        for (int a = 0; a < keys.length; a++) for (int k : keys[a]) bindings.put("key:" + k, a);
        for (int axis : new int[]{MotionEvent.AXIS_X, MotionEvent.AXIS_Z, MotionEvent.AXIS_HAT_X}) {
            bindings.put("axis:" + axis + ":-1", 0); bindings.put("axis:" + axis + ":1", 1);
        }
        for (int axis : new int[]{MotionEvent.AXIS_Y, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_Y}) {
            bindings.put("axis:" + axis + ":-1", 2); bindings.put("axis:" + axis + ":1", 3);
        }
    }
    private void saveBindings() {
        SharedPreferences.Editor ed = prefs.edit();
        for (String k : prefs.getAll().keySet()) if (k.startsWith("bind:")) ed.remove(k);
        for (Map.Entry<String, Integer> e : bindings.entrySet()) ed.putInt("bind:" + e.getKey(), e.getValue());
        ed.putBoolean("custom", true).apply();
    }
    private void refreshBindings() {
        for (int i = 0; i < 5; i++) {
            StringBuilder s = new StringBuilder(RemoteClient.LABELS[i] + "：");
            for (Map.Entry<String, Integer> e : bindings.entrySet()) if (e.getValue() == i) {
                String k = e.getKey();
                s.append(k.startsWith("key:") ? KeyEvent.keyCodeToString(Integer.parseInt(k.substring(4))) : k).append(" ");
            }
            bindingButtons[i].setText(s);
        }
    }
    private void press(String physical, String binding) {
        if (learning >= 0) {
            bindings.put(binding, learning); learning = -1;
            saveBindings(); refreshBindings(); status.setText("已绑定 " + binding + "；松开后再按测试");
            return;
        }
        Integer a = bindings.get(binding);
        if (a == null) return;
        held.put(physical, a);
        if (gate.press(physical, a, SystemClock.uptimeMillis())) send(a);
    }
    private void release(String physical) { held.remove(physical); sleepHeld.remove(physical); gate.release(physical); }
    private void clearInput() { held.clear(); axisHeld.clear(); sleepHeld.clear(); gate.clear(); }

    private void updateMediaSession() {
        if (mediaSession != null)
            mediaSession.setActive(active && capture.isChecked() && musicMode.isChecked());
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        return handleKey(e) || super.dispatchKeyEvent(e);
    }
    private boolean handleKey(KeyEvent e) {
        if (!active || !capture.isChecked() || host.hasFocus()) return false;
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_HOME || k == KeyEvent.KEYCODE_POWER || k == KeyEvent.KEYCODE_BACK)
            return false;
        String physical = e.getDeviceId() + ":key:" + k, binding = "key:" + k;
        if (musicMode.isChecked() && learning < 0) {
            int action = k == KeyEvent.KEYCODE_VOLUME_UP ? 2
                : k == KeyEvent.KEYCODE_VOLUME_DOWN ? 3 : -1;
            boolean pause = k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE;
            if (action >= 0 || pause) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                    input.setText("最近输入：" + KeyEvent.keyCodeToString(k));
                    Log.i("KindleRemoteInput", KeyEvent.keyCodeToString(k));
                    if (pause) { if (sleepHeld.add(physical)) send(4); }
                    else send(action);
                } else if (e.getAction() == KeyEvent.ACTION_UP) release(physical);
                return true;
            }
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            Log.i("KindleRemoteInput", KeyEvent.keyCodeToString(k));
            input.setText("最近输入：" + (e.getDevice() == null ? "设备" : e.getDevice().getName())
                + " / " + KeyEvent.keyCodeToString(k));
            boolean used = learning >= 0 || bindings.containsKey(binding);
            if (used && e.getRepeatCount() == 0) press(physical, binding);
            return used;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) {
            boolean used = held.containsKey(physical) || bindings.containsKey(binding);
            release(physical); return used;
        }
        return false;
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (!capture.isChecked() || !e.isFromSource(InputDevice.SOURCE_JOYSTICK))
            return super.dispatchGenericMotionEvent(e);
        boolean used = false;
        for (int axis : AXES) {
            String id = e.getDeviceId() + ":axis:" + axis;
            float value = e.getAxisValue(axis);
            String previous = axisHeld.get(id);
            if (previous != null && (Math.abs(value) < 0.25f
                || (value < 0 ? -1 : 1) != Integer.parseInt(previous.substring(previous.lastIndexOf(':') + 1)))) {
                release(id); axisHeld.remove(id); previous = null;
            }
            if (Math.abs(value) > 0.65f && previous == null) {
                String binding = "axis:" + axis + ":" + (value < 0 ? -1 : 1);
                boolean wasLearning = learning >= 0;
                used |= learning >= 0 || bindings.containsKey(binding);
                input.setText("最近输入：" + MotionEvent.axisToString(axis) + " " + (value < 0 ? "−" : "+"));
                axisHeld.put(id, binding); press(id, binding);
                if (wasLearning) break;
            } else if (previous != null) used |= bindings.containsKey(previous);
        }
        return used || super.dispatchGenericMotionEvent(e);
    }

    private void send(int action) { submit(action); }
    private void submit(int action) {
        final RemoteClient client;
        try { client = new RemoteClient(host.getText().toString(), "8080"); }
        catch (IllegalArgumentException e) { status.setText(e.getMessage()); return; }
        prefs.edit().putString("host", host.getText().toString().trim())
            .apply();
        final long generation = session;
        try {
            network.execute(() -> {
                if (generation != session) return;
                String result;
                try { result = action < 0 ? client.ping() : client.command(action); }
                catch (Exception e) { result = "连接失败：" + e.getMessage() + "；检查 Wi-Fi、IP 和 Kindle 接收服务。未自动重试。"; }
                final String message = result;
                handler.post(() -> { if (active && generation == session) status.setText(message); });
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            status.setText("连接正忙，已丢弃此按键；避免延迟连翻");
        }
    }
    @Override protected void onResume() {
        super.onResume(); active = true; updateMediaSession(); handler.post(poll);
    }
    @Override protected void onPause() {
        active = false; updateMediaSession();
        session++; learning = -1; clearInput(); network.getQueue().clear();
        handler.removeCallbacks(poll); super.onPause();
    }
    @Override protected void onDestroy() { if (mediaSession != null) mediaSession.release(); network.shutdownNow(); super.onDestroy(); }
}
