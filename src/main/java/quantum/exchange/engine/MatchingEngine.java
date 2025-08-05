package quantum.exchange.engine;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import quantum.exchange.memory.MmapOrderBookManager;
import quantum.exchange.memory.InMemoryChronicleMapManager;
import quantum.exchange.model.Order;
import quantum.exchange.model.MarketData;
import quantum.exchange.orderbook.OrderBook;
import quantum.exchange.queue.OrderQueue;
import quantum.exchange.queue.TradeResultQueue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 거래소의 핵심 매칭 엔진
 * 주문을 처리하고 거래를 실행하는 단일 스레드 엔진이다.
 */
@Slf4j
@RequiredArgsConstructor
public class MatchingEngine implements Runnable {
    
    private final MmapOrderBookManager memoryManager;
    private final InMemoryChronicleMapManager chronicleMapManager;
    @Getter
    private OrderQueue orderQueue;
    @Getter
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
    
    
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            try {
                memoryManager.initialize();
                chronicleMapManager.initialize();
                
                this.orderQueue = new OrderQueue(memoryManager);
                this.tradeQueue = new TradeResultQueue(memoryManager);
                
                addSymbol("BTC-USD");
                addSymbol("ETH-USD");
                addSymbol("BNB-USD");
                addSymbol("ADA-USD");
                addSymbol("SOL-USD");
                
                log.info("매칭 엔진 초기화 완료: {} 개 심볼", orderBooks.size());
                log.info("주문 큐: {}", orderQueue);
                log.info("거래 큐: {}", tradeQueue);
                
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
            log.info("매칭 엔진 시작됨");
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
            log.info("매칭 엔진 중지됨");
        }
    }
    
    @Override
    public void run() {
        log.info("매칭 엔진 처리 스레드 시작됨");
        
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                processNextOrder();
            } catch (Exception e) {
                log.error("주문 처리 중 오류 발생", e);
            }
        }
        
        log.info("매칭 엔진 처리 스레드 중지됨");
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
                log.warn("알 수 없는 심볼 해시: {}", order.getSymbolHash());
                return;
            }
            
            OrderBook orderBook = orderBooks.get(symbol);
            if (orderBook == null) {
                log.warn("심볼에 대한 호가창을 찾을 수 없음: {}", symbol);
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
            log.error("최대 심볼 수 도달: {}", quantum.exchange.memory.SharedMemoryLayout.MAX_SYMBOLS);
            return false;
        }
        
        int symbolIndex = nextSymbolIndex++;
        OrderBook orderBook = new OrderBook(symbol, symbolIndex, chronicleMapManager, memoryManager.getBuffer(), tradeQueue);
        orderBooks.put(symbol, orderBook);
        symbolIndexMap.put(symbol, symbolIndex);
        
        MarketData marketData = new MarketData();
        marketData.setSymbolHash(symbol.hashCode());
        marketDataMap.put(symbol, marketData);
        
        log.info("심볼 추가됨: {} (인덱스: {})", symbol, symbolIndex);
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

    public Map<String, OrderBook> getOrderBooks() {
        return Map.copyOf(orderBooks);
    }

        public record EngineStatistics(long processedOrders, long processedTrades, long lastProcessTime,
                                       long orderQueueSize, long tradeQueueSize, int symbolCount, boolean running) {

        public double getLastProcessTimeMs() {
            return lastProcessTime / 1_000_000.0;
        }

        public double getLastProcessTimeMicros() {
            return lastProcessTime / 1_000.0;
        }

            @Override
            public String toString() {
                return String.format("EngineStats{orders=%d, trades=%d, lastProcess=%.2fμs, queueSize=%d, symbols=%d, running=%s}",
                        processedOrders, processedTrades, getLastProcessTimeMicros(), orderQueueSize, symbolCount, running);
            }
        }
}