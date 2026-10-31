package risk_assessor;

import java.time.LocalDateTime;

/** One COD order. Status is DELIVERED, RTO or UNKNOWN (for brand-new orders). */
public record Order(String id, LocalDateTime time, String name, String phone, String address,
                    String city, String state, String pincode, int value, int numItems,
                    int priorOrders, int priorRto, String utm, String courier, String status) {
    public boolean firstTime() { return priorOrders == 0; }
    public boolean isRto() { return status.equals("RTO"); }
}
