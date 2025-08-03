package quantum.exchange.service;

import quantum.exchange.engine.MatchingEngine;
import quantum.exchange.memory.MmapOrderBookManager;
import quantum.exchange.model.*;
import quantum.exchange.orderbook.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ExchangeService {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeService.class);
    
    private static final String MMAP_FILE_PATH = "./data/exchange.mmap";
    
    private MmapOrderBookManager memoryManager;
    private MatchingEngine matchingEngine;
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    
    @PostConstruct
    public void initialize() {
        try {
            memoryManager = new MmapOrderBookManager(MMAP_FILE_PATH);
            matchingEngine = new MatchingEngine(memoryManager);
            
            matchingEngine.initialize();
            matchingEngine.start();
            
            logger.info("ExchangeService initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize ExchangeService", e);
            throw new RuntimeException("Exchange initialization failed", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            if (matchingEngine != null) {
                matchingEngine.stop();
            }
            if (memoryManager != null) {
                memoryManager.close();
            }
            logger.info("ExchangeService shutdown completed");
        } catch (Exception e) {
            logger.error("Error during ExchangeService shutdown", e);
        }
    }
    
    public Long submitOrder(String symbol, OrderSide side, OrderType type, long price, long quantity) {
        if (!isValidOrder(symbol, side, type, price, quantity)) {
            return null;
        }
        
        long orderId = orderIdGenerator.getAndIncrement();
        Order order = new Order(orderId, symbol, side, type, price, quantity, System.nanoTime());
        
        boolean submitted = matchingEngine.submitOrder(order);
        return submitted ? orderId : null;
    }
    
    public Long submitMarketOrder(String symbol, OrderSide side, long quantity) {
        return submitOrder(symbol, side, OrderType.MARKET, 0, quantity);
    }
    
    public Long submitLimitOrder(String symbol, OrderSide side, long price, long quantity) {
        return submitOrder(symbol, side, OrderType.LIMIT, price, quantity);
    }
    
    private boolean isValidOrder(String symbol, OrderSide side, OrderType type, long price, long quantity) {
        if (symbol == null || symbol.trim().isEmpty()) {
            logger.warn("Invalid symbol: {}", symbol);
            return false;
        }
        
        if (side == null) {
            logger.warn("Invalid order side: null");
            return false;
        }
        
        if (type == null) {
            logger.warn("Invalid order type: null");
            return false;
        }
        
        if (quantity <= 0) {
            logger.warn("Invalid quantity: {}", quantity);
            return false;
        }
        
        if (type == OrderType.LIMIT && price <= 0) {
            logger.warn("Invalid limit order price: {}", price);
            return false;
        }
        
        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        if (orderBook == null) {
            logger.warn("Unknown symbol: {}", symbol);
            return false;
        }
        
        return true;
    }
    
    public OrderBookSnapshot getOrderBookSnapshot(String symbol) {
        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        if (orderBook == null) {
            return null;
        }
        
        List<PriceLevel> bidLevels = orderBook.getTopBidLevels(10);
        List<PriceLevel> askLevels = orderBook.getTopAskLevels(10);
        
        return new OrderBookSnapshot(symbol, bidLevels, askLevels, 
                orderBook.getBestBidPrice(), orderBook.getBestAskPrice(), 
                orderBook.getSpread(), System.nanoTime());
    }
    
    public MarketData getMarketData(String symbol) {
        return matchingEngine.getMarketData(symbol);
    }
    
    public Map<String, MarketData> getAllMarketData() {
        return matchingEngine.getOrderBooks().keySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        symbol -> symbol,
                        symbol -> matchingEngine.getMarketData(symbol)
                ));
    }
    
    public MatchingEngine.EngineStatistics getEngineStatistics() {
        return matchingEngine.getStatistics();
    }
    
    public ExchangeStatus getExchangeStatus() {
        MatchingEngine.EngineStatistics stats = matchingEngine.getStatistics();
        return new ExchangeStatus(
                matchingEngine.isRunning(),
                stats.getProcessedOrders(),
                stats.getProcessedTrades(),
                stats.getLastProcessTimeMicros(),
                stats.getOrderQueueSize(),
                stats.getTradeQueueSize(),
                stats.getSymbolCount(),
                System.nanoTime()
        );
    }
    
    public List<String> getAvailableSymbols() {
        return List.copyOf(matchingEngine.getOrderBooks().keySet());
    }
    
    public boolean addSymbol(String symbol) {
        return matchingEngine.addSymbol(symbol);
    }
    
    public static class OrderBookSnapshot {
        private final String symbol;
        private final List<PriceLevel> bidLevels;
        private final List<PriceLevel> askLevels;
        private final long bestBid;
        private final long bestAsk;
        private final long spread;
        private final long timestamp;
        
        public OrderBookSnapshot(String symbol, List<PriceLevel> bidLevels, List<PriceLevel> askLevels,
                               long bestBid, long bestAsk, long spread, long timestamp) {
            this.symbol = symbol;
            this.bidLevels = bidLevels;
            this.askLevels = askLevels;
            this.bestBid = bestBid;
            this.bestAsk = bestAsk;
            this.spread = spread;
            this.timestamp = timestamp;
        }
        
        public String getSymbol() { return symbol; }
        public List<PriceLevel> getBidLevels() { return bidLevels; }
        public List<PriceLevel> getAskLevels() { return askLevels; }
        public long getBestBid() { return bestBid; }
        public long getBestAsk() { return bestAsk; }
        public long getSpread() { return spread; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class ExchangeStatus {
        private final boolean running;
        private final long processedOrders;
        private final long processedTrades;
        private final double avgProcessingTimeMicros;
        private final long orderQueueSize;
        private final long tradeQueueSize;
        private final int symbolCount;
        private final long timestamp;
        
        public ExchangeStatus(boolean running, long processedOrders, long processedTrades,
                            double avgProcessingTimeMicros, long orderQueueSize, long tradeQueueSize,
                            int symbolCount, long timestamp) {
            this.running = running;
            this.processedOrders = processedOrders;
            this.processedTrades = processedTrades;
            this.avgProcessingTimeMicros = avgProcessingTimeMicros;
            this.orderQueueSize = orderQueueSize;
            this.tradeQueueSize = tradeQueueSize;
            this.symbolCount = symbolCount;
            this.timestamp = timestamp;
        }
        
        public boolean isRunning() { return running; }
        public long getProcessedOrders() { return processedOrders; }
        public long getProcessedTrades() { return processedTrades; }
        public double getAvgProcessingTimeMicros() { return avgProcessingTimeMicros; }
        public long getOrderQueueSize() { return orderQueueSize; }
        public long getTradeQueueSize() { return tradeQueueSize; }
        public int getSymbolCount() { return symbolCount; }
        public long getTimestamp() { return timestamp; }
    }
}