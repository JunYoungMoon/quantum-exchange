package quantum.exchange.memory;

import quantum.exchange.model.Order;
import quantum.exchange.model.Trade;
import quantum.exchange.model.MarketData;
import quantum.exchange.model.PriceLevel;

public class SharedMemoryLayout {
    
    public static final int HEADER_SIZE = 64;
    public static final int ORDER_QUEUE_CAPACITY = 1024 * 1024;
    public static final int TRADE_QUEUE_CAPACITY = 1024 * 1024;
    public static final int MAX_PRICE_LEVELS = 10000;
    public static final int MAX_SYMBOLS = 1000;
    
    public static final int ORDER_QUEUE_SIZE = ORDER_QUEUE_CAPACITY * Order.BYTE_SIZE;
    public static final int TRADE_QUEUE_SIZE = TRADE_QUEUE_CAPACITY * Trade.BYTE_SIZE;
    public static final int MARKET_DATA_SIZE = MAX_SYMBOLS * MarketData.BYTE_SIZE;
    public static final int PRICE_LEVELS_SIZE = MAX_SYMBOLS * MAX_PRICE_LEVELS * PriceLevel.BYTE_SIZE * 2; // bid + ask
    
    public static final int HEADER_OFFSET = 0;
    public static final int ORDER_QUEUE_OFFSET = HEADER_OFFSET + HEADER_SIZE;
    public static final int TRADE_QUEUE_OFFSET = ORDER_QUEUE_OFFSET + ORDER_QUEUE_SIZE;
    public static final int MARKET_DATA_OFFSET = TRADE_QUEUE_OFFSET + TRADE_QUEUE_SIZE;
    public static final int PRICE_LEVELS_OFFSET = MARKET_DATA_OFFSET + MARKET_DATA_SIZE;
    
    public static final int TOTAL_SIZE = PRICE_LEVELS_OFFSET + PRICE_LEVELS_SIZE;
    
    public static class Header {
        public static final int ORDER_QUEUE_HEAD_OFFSET = 0;
        public static final int ORDER_QUEUE_TAIL_OFFSET = 8;
        public static final int TRADE_QUEUE_HEAD_OFFSET = 16;
        public static final int TRADE_QUEUE_TAIL_OFFSET = 24;
        public static final int NEXT_TRADE_ID_OFFSET = 32;
        public static final int TIMESTAMP_OFFSET = 40;
        public static final int VERSION_OFFSET = 48;
        public static final int STATUS_OFFSET = 56;
    }
    
    public static int getSymbolMarketDataOffset(int symbolIndex) {
        return MARKET_DATA_OFFSET + (symbolIndex * MarketData.BYTE_SIZE);
    }
    
    public static int getSymbolBidLevelsOffset(int symbolIndex) {
        return PRICE_LEVELS_OFFSET + (symbolIndex * MAX_PRICE_LEVELS * PriceLevel.BYTE_SIZE * 2);
    }
    
    public static int getSymbolAskLevelsOffset(int symbolIndex) {
        return getSymbolBidLevelsOffset(symbolIndex) + (MAX_PRICE_LEVELS * PriceLevel.BYTE_SIZE);
    }
    
    public static int getOrderOffset(int index) {
        return ORDER_QUEUE_OFFSET + (index * Order.BYTE_SIZE);
    }
    
    public static int getTradeOffset(int index) {
        return TRADE_QUEUE_OFFSET + (index * Trade.BYTE_SIZE);
    }
    
    public static boolean isValidSymbolIndex(int symbolIndex) {
        return symbolIndex >= 0 && symbolIndex < MAX_SYMBOLS;
    }
    
    public static boolean isValidOrderIndex(int index) {
        return index >= 0 && index < ORDER_QUEUE_CAPACITY;
    }
    
    public static boolean isValidTradeIndex(int index) {
        return index >= 0 && index < TRADE_QUEUE_CAPACITY;
    }
    
    public static boolean isValidPriceLevelIndex(int index) {
        return index >= 0 && index < MAX_PRICE_LEVELS;
    }
    
    public static String getLayoutInfo() {
        return String.format("""
            SharedMemoryLayout:
            Total Size: %d bytes (%.2f MB)
            Header: %d bytes at offset %d
            Order Queue: %d bytes at offset %d (capacity: %d orders)
            Trade Queue: %d bytes at offset %d (capacity: %d trades)
            Market Data: %d bytes at offset %d (max symbols: %d)
            Price Levels: %d bytes at offset %d (max levels per symbol: %d)
            """,
            TOTAL_SIZE, TOTAL_SIZE / (1024.0 * 1024.0),
            HEADER_SIZE, HEADER_OFFSET,
            ORDER_QUEUE_SIZE, ORDER_QUEUE_OFFSET, ORDER_QUEUE_CAPACITY,
            TRADE_QUEUE_SIZE, TRADE_QUEUE_OFFSET, TRADE_QUEUE_CAPACITY,
            MARKET_DATA_SIZE, MARKET_DATA_OFFSET, MAX_SYMBOLS,
            PRICE_LEVELS_SIZE, PRICE_LEVELS_OFFSET, MAX_PRICE_LEVELS
        );
    }
}