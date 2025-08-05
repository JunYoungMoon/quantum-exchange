package quantum.exchange.model;

import lombok.Getter;

@Getter
public enum OrderSide {
    BUY(0),
    SELL(1);
    
    private final int value;
    
    OrderSide(int value) {
        this.value = value;
    }

    public static OrderSide fromValue(int value) {
        return switch (value) {
            case 0 -> BUY;
            case 1 -> SELL;
            default -> {
                // Log warning and return default instead of throwing exception
                System.err.println("Warning: Invalid OrderSide value: " + value + ", defaulting to BUY");
                yield BUY;
            }
        };
    }
}