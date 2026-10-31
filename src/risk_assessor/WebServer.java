package risk_assessor;

import risk_assessor.Decision.Action;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Serves the dashboard, the WhatsApp chat simulator and a small JSON API. Built into Java, no libraries. */
public class WebServer {
    private final Config cfg; private final DecisionEngine engine; private final Economics eco;
    private final List<Order> orders; private final Map<String, Order> byId = new LinkedHashMap<>();
    private final Map<String, Decision> decisions = new LinkedHashMap<>();
    private final WhatsAppFlow wa; private final double scale;

    public WebServer(Config cfg, DecisionEngine engine, Economics eco, List<Order> orders, LlmJudge judge) {
        this.cfg = cfg; this.engine = engine; this.eco = eco; this.orders = orders; this.wa = new WhatsAppFlow(cfg);
        for (Order o : orders) { byId.put(o.id(), o); decisions.put(o.id(), engine.decide(o)); }
        this.scale = cfg.d("monthly_cod_orders") / orders.size();
    }

    public void start(int port) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/", ex -> page(ex, "web/dashboard.html"));
        s.createContext("/chat", ex -> page(ex, "web/chat.html"));
        s.createContext("/api/dashboard", ex -> api(ex, this::dashboard));
        s.createContext("/api/orders", ex -> api(ex, this::orderList));
        s.createContext("/api/score", ex -> api(ex, this::scoreNew));
        s.createContext("/api/chat/list", ex -> api(ex, this::chatList));
        s.createContext("/api/chat/start", ex -> api(ex, q -> chat(wa.start(byId.get(q.get("orderId")), decisions.get(q.get("orderId"))))));
        s.createContext("/api/chat/reply", ex -> api(ex, q -> chat(wa.reply(q.get("orderId"), q.getOrDefault("text", "")))));
        s.createContext("/api/chat/tick", ex -> api(ex, q -> chat(wa.tick(q.get("orderId"), Integer.parseInt(q.getOrDefault("hours", "12"))))));
        s.start();
        System.out.println("\nRunning!  Dashboard: http://localhost:" + port + "/    WhatsApp simulator: http://localhost:" + port + "/chat");
        System.out.println("Press Ctrl+C to stop.");
    }

    // ---------------- API handlers (each returns a JSON string) ----------------
    private String dashboard(Map<String, String> q) {
        List<LocalDate> days = orders.stream().map(o -> o.time().toLocalDate()).distinct().sorted().collect(Collectors.toList());
        LocalDate day = q.containsKey("date") ? LocalDate.parse(q.get("date")) : days.get(days.size() - 1);
        List<Order> sel = orders.stream().filter(o -> o.time().toLocalDate().equals(day)).collect(Collectors.toList());
        Map<Action, Integer> cnt = new EnumMap<>(Action.class); for (Action a : Action.values()) cnt.put(a, 0);
        double saved = 0, lost = 0, fees = 0;
        for (Order o : sel) { Decision d = decisions.get(o.id()); cnt.merge(d.action(), 1, Integer::sum); saved += d.saved(); lost += d.lost(); fees += d.discountsAndFees(); }
        StringBuilder b = new StringBuilder("{");
        b.append("\"date\":").append(Json.s(day.toString())).append(",\"sampleOrders\":").append(sel.size()).append(",\"scale\":").append(Json.n(scale));
        b.append(",\"buckets\":[");
        int i = 0; for (Action a : Action.values()) { if (i++ > 0) b.append(','); b.append("{\"key\":").append(Json.s(a.name())).append(",\"label\":").append(Json.s(a.label)).append(",\"orders\":").append(Math.round(cnt.get(a) * scale)).append('}'); }
        b.append("],\"saved\":").append(Json.n(saved * scale)).append(",\"lost\":").append(Json.n(lost * scale)).append(",\"fees\":").append(Json.n(fees * scale));
        b.append(",\"net\":").append(Json.n((saved - lost - fees) * scale)).append(",\"rtoCost\":").append(Json.n(eco.rtoLoss()));
        b.append(",\"days\":[").append(days.stream().map(d -> Json.s(d.toString())).collect(Collectors.joining(","))).append("]}");
        return b.toString();
    }

    private String orderList(Map<String, String> q) {
        String day = q.get("date"); String act = q.get("action");
        List<String> rows = new ArrayList<>();
        for (Order o : orders) {
            if (day != null && !o.time().toLocalDate().toString().equals(day)) continue;
            Decision d = decisions.get(o.id()); if (act != null && !d.action().name().equals(act)) continue;
            rows.add(row(o, d));
        }
        return "[" + String.join(",", rows) + "]";
    }
    private String row(Order o, Decision d) {
        return "{\"id\":" + Json.s(o.id()) + ",\"name\":" + Json.s(o.name()) + ",\"state\":" + Json.s(o.state()) + ",\"value\":" + o.value()
            + ",\"action\":" + Json.s(d.action().label) + ",\"key\":" + Json.s(d.action().name()) + ",\"risk\":" + Json.n(d.risk() * 100)
            + ",\"discount\":" + d.discount() + ",\"reason\":" + Json.s(d.reason()) + "}";
    }

    /** Score a brand-new order typed into the form (urlencoded fields). */
    private String scoreNew(Map<String, String> q) {
        Order o = new Order("NEW" + (System.currentTimeMillis() % 100000), java.time.LocalDateTime.now(), q.getOrDefault("name", ""),
            q.getOrDefault("phone", ""), q.getOrDefault("address", ""), q.getOrDefault("city", ""), q.getOrDefault("state", ""), q.getOrDefault("pincode", ""),
            Integer.parseInt(q.getOrDefault("value", "649")), 1, Integer.parseInt(q.getOrDefault("priorOrders", "0")),
            Integer.parseInt(q.getOrDefault("priorRto", "0")), "meta_ads", "Delhivery", "UNKNOWN");
        Decision d = engine.decide(o); byId.put(o.id(), o); decisions.put(o.id(), d);
        return row(o, d);
    }

    private String chatList(Map<String, String> q) {
        List<String> rows = new ArrayList<>();
        for (Order o : byId.values()) { Decision d = decisions.get(o.id());
            if ((d.action() == Action.CONFIRM || d.action() == Action.NUDGE) && rows.size() < 40) rows.add(row(o, d)); }
        // newest typed-in orders first
        Collections.reverse(rows);
        return "[" + String.join(",", rows) + "]";
    }
    private String chat(WhatsAppFlow.Chat c) {
        if (c == null) return "{\"error\":\"Pick an order that is in 'Ship after confirmation' or 'Nudge to prepaid'.\"}";
        String msgs = c.messages.stream().map(m -> "{\"from\":" + Json.s(m[0]) + ",\"text\":" + Json.s(m[1]) + "}").collect(Collectors.joining(","));
        return "{\"orderId\":" + Json.s(c.orderId) + ",\"kind\":" + Json.s(c.kind.label) + ",\"state\":" + Json.s(c.state) + ",\"hours\":" + c.hoursPassed + ",\"messages\":[" + msgs + "]}";
    }

    // ---------------- plumbing ----------------
    private interface Handler { String handle(Map<String, String> q) throws Exception; }
    private void api(HttpExchange ex, Handler h) throws java.io.IOException {
        String out; int code = 200;
        try {
            Map<String, String> q = params(ex.getRequestURI().getRawQuery());
            if ("POST".equals(ex.getRequestMethod())) q.putAll(params(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            out = h.handle(q);
        } catch (Exception e) { code = 400; out = "{\"error\":" + Json.s("Something went wrong: " + e.getMessage()) + "}"; }
        send(ex, code, "application/json; charset=utf-8", out);
    }
    private void page(HttpExchange ex, String file) throws java.io.IOException {
        String path = ex.getRequestURI().getPath();
        if (!path.equals("/") && !path.equals("/chat")) { send(ex, 404, "text/plain", "Not found"); return; }
        send(ex, 200, "text/html; charset=utf-8", Files.readString(Path.of(file)));
    }
    private void send(HttpExchange ex, int code, String type, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type); ex.sendResponseHeaders(code, b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }
    private static Map<String, String> params(String raw) {
        Map<String, String> m = new HashMap<>(); if (raw == null || raw.isBlank()) return m;
        for (String kv : raw.split("&")) { int i = kv.indexOf('='); if (i < 0) continue;
            m.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8)); }
        return m;
    }
}
