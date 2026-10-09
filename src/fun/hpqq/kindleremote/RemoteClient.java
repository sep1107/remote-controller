package fun.hpqq.kindleremote;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Sends one command once: retrying a timed-out page turn could turn twice. */
public final class RemoteClient {
    public static final String[] LABELS = {"上一页", "下一页", "亮度 +", "亮度 −", "休眠"};
    private static final String[] EVENTS = {"GotoViewRel/-1", "GotoViewRel/1",
        "IncreaseFlIntensity/1", "DecreaseFlIntensity/1", "RequestSuspend"};
    private final String base;

    public RemoteClient(String host, String port) {
        host = host.trim();
        if (!host.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))
            throw new IllegalArgumentException("请输入 Kindle 的 IPv4 地址，例如 192.168.1.42");
        for (String part : host.split("\\."))
            if (Integer.parseInt(part) > 255) throw new IllegalArgumentException("IP 地址无效");
        int p;
        try { p = Integer.parseInt(port.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("端口应为 1–65535"); }
        if (p < 1 || p > 65535) throw new IllegalArgumentException("端口应为 1–65535");
        base = "http://" + host + ":" + p;
    }

    public String command(int action) throws Exception {
        if (action < 0 || action >= EVENTS.length) throw new IllegalArgumentException("未知操作");
        String body = request("/koreader/event/" + EVENTS[action]);
        String ack = "Event sent: " + EVENTS[action].split("/")[0];
        if (!body.contains(ack))
            throw new IllegalStateException("响应不是目标服务的事件确认，请检查地址和端口");
        return "已发送：" + LABELS[action] + "（请在 Kindle 确认效果）";
    }

    public String ping() throws Exception {
        String body = request("/koreader/event/");
        if (!body.contains("High-level KOReader events"))
            throw new IllegalStateException("该端口不是所选遥控服务");
        return "已连接 Kindle 控制器；打开书后可测试翻页";
    }

    private String request(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setConnectTimeout(1500);
        c.setReadTimeout(2000);
        c.setInstanceFollowRedirects(false);
        c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 262144) throw new IllegalStateException("响应过大");
                    out.write(buffer, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally { c.disconnect(); }
    }
}
