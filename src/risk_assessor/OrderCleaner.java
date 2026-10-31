package risk_assessor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Loads the CSV and fixes the data problems we found (duplicates, mixed-case status, etc). */
public class OrderCleaner {
    public int rawRows, duplicates, inTransit, zeroValue;
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<Order> load(String path) throws Exception {
        List<Map<String, String>> rows = Csv.read(path);
        rawRows = rows.size();
        Set<String> seen = new HashSet<>();
        List<Order> out = new ArrayList<>();
        for (Map<String, String> r : rows) {
            String key = String.join("|", r.values());          // exact duplicate = identical row
            if (!seen.add(key)) { duplicates++; continue; }
            String status = r.get("final_status").toUpperCase();
            if (!status.equals("DELIVERED") && !status.equals("RTO")) { inTransit++; continue; } // outcome unknown
            int value = Integer.parseInt(r.get("order_value"));
            if (value <= 0) { zeroValue++; continue; }                                            // data error
            String pin = r.get("pincode");
            if (pin.endsWith(".0")) pin = pin.substring(0, pin.length() - 2);                    // keep pincode as text
            out.add(new Order(r.get("order_id"), LocalDateTime.parse(r.get("order_datetime"), F),
                    r.get("customer_name"), r.get("phone"), r.get("address"), r.get("city"), r.get("state"), pin,
                    value, Integer.parseInt(r.get("num_items")), Integer.parseInt(r.get("customer_prior_orders")),
                    Integer.parseInt(r.get("customer_prior_rto")), r.get("utm_source"), r.get("courier"), status));
        }
        return out;
    }

    public String summary(int kept) {
        return String.format("Loaded %d rows -> kept %d  (removed: %d duplicates, %d in transit, %d with Rs.0 value)",
                rawRows, kept, duplicates, inTransit, zeroValue);
    }
}
