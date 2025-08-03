package quantum.exchange.model;

import java.nio.ByteBuffer;

public class MarketData {
    public static final int BYTE_SIZE = Long.BYTES * 6 + Integer.BYTES;
    
    private int symbolHash;
    private long lastPrice;
    private long lastQuantity;
    private long volume24h;
    private long bestBidPrice;
    private long bestAskPrice;
    private long timestamp;
    
    public MarketData() {}
    
    public MarketData(int symbolHash, long lastPrice, long lastQuantity, 
                      long volume24h, long bestBidPrice, long bestAskPrice, long timestamp) {
        this.symbolHash = symbolHash;
        this.lastPrice = lastPrice;
        this.lastQuantity = lastQuantity;
        this.volume24h = volume24h;
        this.bestBidPrice = bestBidPrice;
        this.bestAskPrice = bestAskPrice;
        this.timestamp = timestamp;
    }
    
    public void writeToBuffer(ByteBuffer buffer) {
        buffer.putInt(symbolHash);
        buffer.putLong(lastPrice);
        buffer.putLong(lastQuantity);
        buffer.putLong(volume24h);
        buffer.putLong(bestBidPrice);
        buffer.putLong(bestAskPrice);
        buffer.putLong(timestamp);
    }
    
    public void readFromBuffer(ByteBuffer buffer) {
        this.symbolHash = buffer.getInt();
        this.lastPrice = buffer.getLong();
        this.lastQuantity = buffer.getLong();
        this.volume24h = buffer.getLong();
        this.bestBidPrice = buffer.getLong();
        this.bestAskPrice = buffer.getLong();
        this.timestamp = buffer.getLong();
    }
    
    public void updateTrade(long price, long quantity) {
        this.lastPrice = price;
        this.lastQuantity = quantity;
        this.volume24h += quantity;
        this.timestamp = System.nanoTime();
    }
    
    public void updateBestBid(long price) {
        this.bestBidPrice = price;
        this.timestamp = System.nanoTime();
    }
    
    public void updateBestAsk(long price) {
        this.bestAskPrice = price;
        this.timestamp = System.nanoTime();
    }
    
    public long getSpread() {
        return bestAskPrice - bestBidPrice;
    }
    
    public int getSymbolHash() { return symbolHash; }
    public void setSymbolHash(int symbolHash) { this.symbolHash = symbolHash; }
    
    public long getLastPrice() { return lastPrice; }
    public void setLastPrice(long lastPrice) { this.lastPrice = lastPrice; }
    
    public long getLastQuantity() { return lastQuantity; }
    public void setLastQuantity(long lastQuantity) { this.lastQuantity = lastQuantity; }
    
    public long getVolume24h() { return volume24h; }
    public void setVolume24h(long volume24h) { this.volume24h = volume24h; }
    
    public long getBestBidPrice() { return bestBidPrice; }
    public void setBestBidPrice(long bestBidPrice) { this.bestBidPrice = bestBidPrice; }
    
    public long getBestAskPrice() { return bestAskPrice; }
    public void setBestAskPrice(long bestAskPrice) { this.bestAskPrice = bestAskPrice; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    @Override
    public String toString() {
        return String.format("MarketData{symbol=%d, last=%d, qty=%d, vol=%d, bid=%d, ask=%d, spread=%d}",
                symbolHash, lastPrice, lastQuantity, volume24h, bestBidPrice, bestAskPrice, getSpread());
    }
}