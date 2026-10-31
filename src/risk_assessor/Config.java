package risk_assessor;

import java.io.FileInputStream;
import java.util.Properties;

/** Reads config/rules.properties. Every number the business may want to change lives there. */
public class Config {
    private final Properties p = new Properties();

    public Config(String path) {
        try (FileInputStream in = new FileInputStream(path)) { p.load(in); }
        catch (Exception e) { throw new RuntimeException("Cannot read " + path + ": " + e.getMessage()); }
    }
    public double d(String k) {
        String v = p.getProperty(k);
        if (v == null) throw new RuntimeException("Missing setting '" + k + "' in rules.properties");
        try { return Double.parseDouble(v.trim()); }
        catch (NumberFormatException e) { throw new RuntimeException("Setting '" + k + "' must be a number, found: " + v); }
    }
    public int i(String k) { return (int) Math.round(d(k)); }
    public boolean b(String k) { return "true".equalsIgnoreCase(p.getProperty(k, "false").trim()); }
    public String s(String k) { return p.getProperty(k, "").trim(); }
}
