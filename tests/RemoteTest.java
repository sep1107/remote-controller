import fun.hpqq.kindleremote.InputGate;
import fun.hpqq.kindleremote.RemoteClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RemoteTest {
    static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    public static void main(String[] args) throws Exception {
        InputGate gate = new InputGate();
        check(gate.press("left", 0, 0), "first page");
        check(!gate.press("left", 0, 500), "no repeat page");
        check(gate.press("right", 1, 500), "separate controller");
        gate.release("left"); check(gate.press("left", 0, 600), "release rearms");
        check(gate.press("light", 2, 0), "brightness first");
        check(!gate.press("light", 2, 200), "brightness limited");
        check(gate.press("light", 2, 300), "brightness repeats");
        check(!gate.press("sleep", 4, 0), "sleep short press blocked");
        check(!gate.press("sleep", 4, 999), "sleep guard");
        check(gate.press("sleep", 4, 1000), "sleep held");
        check(!gate.press("sleep", 4, 2000), "sleep once");
        gate.clear(); check(!gate.press("sleep", 4, 2100), "pause clears held state");
        for (String host : new String[]{"256.1.1.1", "http://example.com", "1.2.3", "1.2.3.4/path"}) {
            try { new RemoteClient(host, "8080"); throw new AssertionError("invalid host accepted"); }
            catch (IllegalArgumentException expected) {}
        }
        for (String port : new String[]{"0", "65536", "x"}) {
            try { new RemoteClient("127.0.0.1", port); throw new AssertionError("invalid port accepted"); }
            catch (IllegalArgumentException expected) {}
        }
        List<String> paths = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath(); paths.add(path);
            String event = path.substring(path.indexOf("event/") + 6).split("/")[0];
            String body = event.isEmpty() ? "High-level KOReader events" : "Event sent: " + event;
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data); exchange.close();
        });
        server.start();
        try {
            RemoteClient client = new RemoteClient("127.0.0.1", "" + server.getAddress().getPort());
            client.ping(); for (int a = 0; a < 5; a++) client.command(a);
            check(paths.toString().equals("[/koreader/event/, /koreader/event/GotoViewRel/-1, /koreader/event/GotoViewRel/1, /koreader/event/IncreaseFlIntensity/1, /koreader/event/DecreaseFlIntensity/1, /koreader/event/RequestSuspend]"), "protocol routes");
        } finally { server.stop(0); }
        HttpServer wrong = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        wrong.createContext("/", exchange -> {
            byte[] data = "unrelated server".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data); exchange.close();
        });
        wrong.start();
        try {
            RemoteClient c = new RemoteClient("127.0.0.1", "" + wrong.getAddress().getPort());
            try { c.ping(); throw new AssertionError("wrong server accepted"); } catch (IllegalStateException expected) {}
            try { c.command(1); throw new AssertionError("false ack accepted"); } catch (IllegalStateException expected) {}
        } finally { wrong.stop(0); }
        System.out.println("PASS: input guards, independent devices, validation, five HTTP actions, wrong-server rejection");
    }
}
