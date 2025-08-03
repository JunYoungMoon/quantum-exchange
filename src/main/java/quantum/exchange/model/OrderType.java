package quantum.exchange.model;

public enum OrderType {
    LIMIT(0),
    MARKET(1);
    
    private final int value;
    
    OrderType(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    public static OrderType fromValue(int value) {
        return switch (value) {
            case 0 -> LIMIT;
            case 1 -> MARKET;
            default -> {
                // Log warning and return default instead of throwing exception
                System.err.println("Warning: Invalid OrderType value: " + value + ", defaulting to LIMIT");
                yield LIMIT;
            }
        };
    }
}