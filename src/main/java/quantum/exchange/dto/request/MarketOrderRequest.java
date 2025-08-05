package quantum.exchange.dto.request;

import lombok.Data;

@Data
public class MarketOrderRequest {
    private String symbol;
    private String side;
    private long quantity;
}