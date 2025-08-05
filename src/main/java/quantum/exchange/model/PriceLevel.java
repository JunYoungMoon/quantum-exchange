package quantum.exchange.model;

import lombok.Getter;
import lombok.Setter;

import java.nio.ByteBuffer;

@Setter
@Getter
public class PriceLevel {
    public static final int BYTE_SIZE = Long.BYTES * 3;
    
    private long price;
    private long totalQuantity;
    private long orderCount;
    
    public PriceLevel() {}
    
    public PriceLevel(long price, long totalQuantity, long orderCount) {
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.orderCount = orderCount;
    }
    
    public void writeToBuffer(ByteBuffer buffer) {
        buffer.putLong(price);
        buffer.putLong(totalQuantity);
        buffer.putLong(orderCount);
    }
    
    public void readFromBuffer(ByteBuffer buffer) {
        this.price = buffer.getLong();
        this.totalQuantity = buffer.getLong();
        this.orderCount = buffer.getLong();
    }
    
    public void addOrder(long quantity) {
        this.totalQuantity += quantity;
        this.orderCount++;
    }
    
    public void removeOrder(long quantity) {
        this.totalQuantity -= quantity;
        this.orderCount--;
        if (this.orderCount < 0) this.orderCount = 0;
        if (this.totalQuantity < 0) this.totalQuantity = 0;
    }
    
    public void updateQuantity(long quantityChange) {
        this.totalQuantity += quantityChange;
        if (this.totalQuantity < 0) this.totalQuantity = 0;
    }
    
    public boolean isEmpty() {
        return orderCount == 0 || totalQuantity == 0;
    }

    @Override
    public String toString() {
        return String.format("PriceLevel{price=%d, qty=%d, orders=%d}",
                price, totalQuantity, orderCount);
    }
}