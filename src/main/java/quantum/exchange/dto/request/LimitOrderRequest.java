package quantum.exchange.dto.request;

import lombok.Data;

@Data
public class LimitOrderRequest {
    private String symbol;
    private String side;
    private long price;
    private long quantity;
}