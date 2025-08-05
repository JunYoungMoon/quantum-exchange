package quantum.exchange.model;

import lombok.Getter;
import lombok.Setter;

import java.nio.ByteBuffer;

@Getter
@Setter
public class Order {
    public static final int BYTE_SIZE = Long.BYTES * 5 + Integer.BYTES * 3;
    
    private long orderId;
    private String symbol;
    private OrderSide side;
    private OrderType type;
    private long price;
    private long quantity;
    private long timestamp;
    private int symbolHash;
    
    public Order() {}
    
    public Order(long orderId, String symbol, OrderSide side, OrderType type, 
                 long price, long quantity, long timestamp) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
        this.symbolHash = symbol.hashCode();
    }
    
    public void writeToBuffer(ByteBuffer buffer) {
        buffer.putLong(orderId);
        buffer.putInt(symbolHash);
        buffer.putInt(side.getValue());
        buffer.putInt(type.getValue());
        buffer.putLong(price);
        buffer.putLong(quantity);
        buffer.putLong(timestamp);
    }
    
    public void readFromBuffer(ByteBuffer buffer) {
        this.orderId = buffer.getLong();
        this.symbolHash = buffer.getInt();
        this.side = OrderSide.fromValue(buffer.getInt());
        this.type = OrderType.fromValue(buffer.getInt());
        this.price = buffer.getLong();
        this.quantity = buffer.getLong();
        this.timestamp = buffer.getLong();
    }
    
    public boolean isValid() {
        return orderId > 0 && quantity > 0 && timestamp > 0 && 
               (type == OrderType.MARKET || type == OrderType.MARKET_WITH_PRICE || 
                type == OrderType.LIMIT) && 
               (type == OrderType.MARKET || price > 0);
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol; 
        this.symbolHash = symbol.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Order{id=%d, symbol='%s', side=%s, type=%s, price=%d, qty=%d, time=%d}",
                orderId, symbol, side, type, price, quantity, timestamp);
    }
}