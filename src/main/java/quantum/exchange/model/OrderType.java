package quantum.exchange.model;

import lombok.Getter;

@Getter
public enum OrderType {
    LIMIT(0),
    MARKET(1),
    MARKET_WITH_PRICE(2);
    
    private final int value;
    
    OrderType(int value) {
        this.value = value;
    }

    public static OrderType fromValue(int value) {
        return switch (value) {
            case 0 -> LIMIT;
            case 1 -> MARKET;
            case 2 -> MARKET_WITH_PRICE;
            default -> {
                // Log warning and return default instead of throwing exception
                System.err.println("Warning: Invalid OrderType value: " + value + ", defaulting to LIMIT");
                yield LIMIT;
            }
        };
    }
}