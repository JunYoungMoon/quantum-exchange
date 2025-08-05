package quantum.exchange.model;

import lombok.Getter;
import lombok.Setter;

import java.nio.ByteBuffer;

@Setter
@Getter
public class Trade {
    public static final int BYTE_SIZE = Long.BYTES * 5 + Integer.BYTES;
    
    private long tradeId;
    private long buyOrderId;
    private long sellOrderId;
    private long price;
    private long quantity;
    private long timestamp;
    private int symbolHash;
    
    public Trade() {}
    
    public Trade(long tradeId, long buyOrderId, long sellOrderId, 
                 long price, long quantity, long timestamp, int symbolHash) {
        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
        this.symbolHash = symbolHash;
    }
    
    public void writeToBuffer(ByteBuffer buffer) {
        buffer.putLong(tradeId);
        buffer.putLong(buyOrderId);
        buffer.putLong(sellOrderId);
        buffer.putLong(price);
        buffer.putLong(quantity);
        buffer.putLong(timestamp);
        buffer.putInt(symbolHash);
    }
    
    public void readFromBuffer(ByteBuffer buffer) {
        this.tradeId = buffer.getLong();
        this.buyOrderId = buffer.getLong();
        this.sellOrderId = buffer.getLong();
        this.price = buffer.getLong();
        this.quantity = buffer.getLong();
        this.timestamp = buffer.getLong();
        this.symbolHash = buffer.getInt();
    }

    @Override
    public String toString() {
        return String.format("Trade{id=%d, buy=%d, sell=%d, price=%d, qty=%d, time=%d}",
                tradeId, buyOrderId, sellOrderId, price, quantity, timestamp);
    }
}