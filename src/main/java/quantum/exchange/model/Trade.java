package quantum.exchange.model;

import java.nio.ByteBuffer;

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
    
    public long getTradeId() { return tradeId; }
    public void setTradeId(long tradeId) { this.tradeId = tradeId; }
    
    public long getBuyOrderId() { return buyOrderId; }
    public void setBuyOrderId(long buyOrderId) { this.buyOrderId = buyOrderId; }
    
    public long getSellOrderId() { return sellOrderId; }
    public void setSellOrderId(long sellOrderId) { this.sellOrderId = sellOrderId; }
    
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }
    
    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public int getSymbolHash() { return symbolHash; }
    public void setSymbolHash(int symbolHash) { this.symbolHash = symbolHash; }
    
    @Override
    public String toString() {
        return String.format("Trade{id=%d, buy=%d, sell=%d, price=%d, qty=%d, time=%d}",
                tradeId, buyOrderId, sellOrderId, price, quantity, timestamp);
    }
}