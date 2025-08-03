package quantum.exchange.engine;

import quantum.exchange.memory.MmapOrderBookManager;
import quantum.exchange.model.Order;
import quantum.exchange.model.MarketData;
import quantum.exchange.orderbook.OrderBook;
import quantum.exchange.queue.OrderQueue;
import quantum.exchange.queue.TradeResultQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MatchingEngine implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MatchingEngine.class);
    
    private final MmapOrderBookManager memoryManager;
    private OrderQueue orderQueue;
    private TradeResultQueue tradeQueue;
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, Integer> symbolIndexMap = new ConcurrentHashMap<>();
    private final Map<String, MarketData> marketDataMap = new ConcurrentHashMap<>();
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicLong processedOrders = new AtomicLong(0);
    private final AtomicLong processedTrades = new AtomicLong(0);
    private final AtomicLong lastProcessTime = new AtomicLong(0);
    
    private volatile Thread processingThread;
    private int nextSymbolIndex = 0;
    
    public MatchingEngine(MmapOrderBookManager memoryManager) {
        this.memoryManager = memoryManager;
        this.orderQueue = null;
        this.tradeQueue = null;
    }
    
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            try {
                memoryManager.initialize();
                
                this.orderQueue = new OrderQueue(memoryManager);
                this.tradeQueue = new TradeResultQueue(memoryManager);
                
                addSymbol("BTC/USD");
                addSymbol("ETH/USD");
                addSymbol("BNB/USD");
                addSymbol("ADA/USD");
                addSymbol("SOL/USD");
                
                logger.info("MatchingEngine initialized with {} symbols", orderBooks.size());
                logger.info("Order queue: {}", orderQueue);
                logger.info("Trade queue: {}", tradeQueue);
                
            } catch (Exception e) {
                initialized.set(false);
                throw new RuntimeException("Failed to initialize MatchingEngine", e);
            }
        }
    }
    
    public void start() {
        if (!initialized.get()) {
            throw new IllegalStateException("MatchingEngine must be initialized before starting");
        }
        
        if (running.compareAndSet(false, true)) {
            memoryManager.setActive(true);
            processingThread = new Thread(this, "MatchingEngine-Thread");
            processingThread.start();
            logger.info("MatchingEngine started");
        }
    }
    
    public void stop() {
        if (running.compareAndSet(true, false)) {
            memoryManager.setActive(false);
            if (processingThread != null) {
                processingThread.interrupt();
                try {
                    processingThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            logger.info("MatchingEngine stopped");
        }
    }
    
    @Override
    public void run() {
        logger.info("MatchingEngine processing thread started");
        
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                processNextOrder();
            } catch (Exception e) {
                logger.error("Error processing order", e);
            }
        }
        
        logger.info("MatchingEngine processing thread stopped");
    }
    
    private void processNextOrder() {
        if (orderQueue == null) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        
        Order order = orderQueue.poll();
        if (order == null) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            return;
        }
        
        long startTime = System.nanoTime();
        
        try {
            String symbol = getSymbolFromHash(order.getSymbolHash());
            if (symbol == null) {
                logger.warn("Unknown symbol hash: {}", order.getSymbolHash());
                return;
            }
            
            OrderBook orderBook = orderBooks.get(symbol);
            if (orderBook == null) {
                logger.warn("No order book found for symbol: {}", symbol);
                return;
            }
            
            List<Long> trades = orderBook.processOrder(order);
            
            if (!trades.isEmpty()) {
                processedTrades.addAndGet(trades.size());
                updateMarketData(symbol, orderBook);
            }
            
            processedOrders.incrementAndGet();
            
        } finally {
            long processingTime = System.nanoTime() - startTime;
            lastProcessTime.set(processingTime);
            memoryManager.updateTimestamp();
        }
    }
    
    private void updateMarketData(String symbol, OrderBook orderBook) {
        MarketData marketData = marketDataMap.get(symbol);
        if (marketData == null) {
            marketData = new MarketData();
            marketData.setSymbolHash(symbol.hashCode());
            marketDataMap.put(symbol, marketData);
        }
        
        marketData.updateBestBid(orderBook.getBestBidPrice());
        marketData.updateBestAsk(orderBook.getBestAskPrice());
        
        writeMarketDataToMemory(symbol, marketData);
    }
    
    private void writeMarketDataToMemory(String symbol, MarketData marketData) {
        Integer symbolIndex = symbolIndexMap.get(symbol);
        if (symbolIndex != null) {
            int offset = quantum.exchange.memory.SharedMemoryLayout.getSymbolMarketDataOffset(symbolIndex);
            memoryManager.getBuffer().position(offset);
            marketData.writeToBuffer(memoryManager.getBuffer());
        }
    }
    
    private String getSymbolFromHash(int symbolHash) {
        for (Map.Entry<String, OrderBook> entry : orderBooks.entrySet()) {
            if (entry.getValue().getSymbolHash() == symbolHash) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    public boolean submitOrder(Order order) {
        if (!running.get() || orderQueue == null) {
            return false;
        }
        
        order.setTimestamp(System.nanoTime());
        return orderQueue.offer(order);
    }
    
    public boolean addSymbol(String symbol) {
        if (orderBooks.containsKey(symbol)) {
            return false;
        }
        
        if (nextSymbolIndex >= quantum.exchange.memory.SharedMemoryLayout.MAX_SYMBOLS) {
            logger.error("Maximum number of symbols reached: {}", quantum.exchange.memory.SharedMemoryLayout.MAX_SYMBOLS);
            return false;
        }
        
        int symbolIndex = nextSymbolIndex++;
        OrderBook orderBook = new OrderBook(symbol, symbolIndex, memoryManager, tradeQueue);
        orderBooks.put(symbol, orderBook);
        symbolIndexMap.put(symbol, symbolIndex);
        
        MarketData marketData = new MarketData();
        marketData.setSymbolHash(symbol.hashCode());
        marketDataMap.put(symbol, marketData);
        
        logger.info("Added symbol: {} (index: {})", symbol, symbolIndex);
        return true;
    }
    
    public OrderBook getOrderBook(String symbol) {
        return orderBooks.get(symbol);
    }
    
    public MarketData getMarketData(String symbol) {
        return marketDataMap.get(symbol);
    }
    
    public EngineStatistics getStatistics() {
        return new EngineStatistics(
                processedOrders.get(),
                processedTrades.get(),
                lastProcessTime.get(),
                orderQueue != null ? orderQueue.size() : 0,
                tradeQueue != null ? tradeQueue.size() : 0,
                orderBooks.size(),
                running.get()
        );
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public boolean isInitialized() {
        return initialized.get();
    }
    
    public OrderQueue getOrderQueue() {
        return orderQueue;
    }
    
    public TradeResultQueue getTradeQueue() {
        return tradeQueue;
    }
    
    public Map<String, OrderBook> getOrderBooks() {
        return Map.copyOf(orderBooks);
    }
    
    public static class EngineStatistics {
        private final long processedOrders;
        private final long processedTrades;
        private final long lastProcessTime;
        private final long orderQueueSize;
        private final long tradeQueueSize;
        private final int symbolCount;
        private final boolean running;
        
        public EngineStatistics(long processedOrders, long processedTrades, long lastProcessTime,
                               long orderQueueSize, long tradeQueueSize, int symbolCount, boolean running) {
            this.processedOrders = processedOrders;
            this.processedTrades = processedTrades;
            this.lastProcessTime = lastProcessTime;
            this.orderQueueSize = orderQueueSize;
            this.tradeQueueSize = tradeQueueSize;
            this.symbolCount = symbolCount;
            this.running = running;
        }
        
        public long getProcessedOrders() { return processedOrders; }
        public long getProcessedTrades() { return processedTrades; }
        public long getLastProcessTime() { return lastProcessTime; }
        public double getLastProcessTimeMs() { return lastProcessTime / 1_000_000.0; }
        public double getLastProcessTimeMicros() { return lastProcessTime / 1_000.0; }
        public long getOrderQueueSize() { return orderQueueSize; }
        public long getTradeQueueSize() { return tradeQueueSize; }
        public int getSymbolCount() { return symbolCount; }
        public boolean isRunning() { return running; }
        
        @Override
        public String toString() {
            return String.format("EngineStats{orders=%d, trades=%d, lastProcess=%.2fμs, queueSize=%d, symbols=%d, running=%s}",
                    processedOrders, processedTrades, getLastProcessTimeMicros(), orderQueueSize, symbolCount, running);
        }
    }
}