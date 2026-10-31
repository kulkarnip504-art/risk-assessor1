package risk_assessor;

/** Minimal JSON writing helpers (we have no libraries). */
public class Json {
    public static String s(String v) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : v.toCharArray()) {
            switch (c) { case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\"); case '\n' -> b.append("\\n");
                case '\r' -> {} case '\t' -> b.append(' '); default -> b.append(c); }
        }
        return b.append('"').toString();
    }
    public static String n(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }
}
