package quantum.exchange.memory;

import quantum.exchange.model.Order;
import quantum.exchange.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of Chronicle Map functionality for testing and development.
 * This avoids the complex JVM access requirements of Chronicle Map during testing.
 */
public class InMemoryChronicleMapManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryChronicleMapManager.class);
    
    private final ConcurrentHashMap<Long, Order> unfilledOrdersMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Trade> pendingTradesMap = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong nextOrderId = new AtomicLong(1);
    private final AtomicLong nextTradeId = new AtomicLong(1);
    
    // In-memory indexes for fast lookups
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Long>> symbolPriceIndex = new ConcurrentHashMap<>();
    
    public void initialize() throws IOException {
        if (initialized.compareAndSet(false, true)) {
            logger.info("InMemoryChronicleMapManager initialized successfully");
        }
    }
    
    // Unfilled Orders Management
    public boolean addUnfilledOrder(Order order) {
        ensureInitialized();
        
        if (order == null || order.getOrderId() <= 0) {
            return false;
        }
        
        try {
            unfilledOrdersMap.put(order.getOrderId(), order);
            
            // Update in-memory index
            if (order.getSymbol() != null) {
                symbolPriceIndex
                    .computeIfAbsent(order.getSymbol(), k -> new ConcurrentHashMap<>())
                    .put(order.getPrice(), order.getOrderId());
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Failed to add unfilled order: {}", order.getOrderId(), e);
            return false;
        }
    }
    
    public Order getUnfilledOrder(long orderId) {
        ensureInitialized();
        return unfilledOrdersMap.get(orderId);
    }
    
    public Order removeUnfilledOrder(long orderId) {
        ensureInitialized();
        
        Order removedOrder = unfilledOrdersMap.remove(orderId);
        if (removedOrder != null && removedOrder.getSymbol() != null) {
            // Update in-memory index
            ConcurrentHashMap<Long, Long> priceMap = symbolPriceIndex.get(removedOrder.getSymbol());
            if (priceMap != null) {
                priceMap.remove(removedOrder.getPrice());
                if (priceMap.isEmpty()) {
                    symbolPriceIndex.remove(removedOrder.getSymbol());
                }
            }
        }
        
        return removedOrder;
    }
    
    public boolean updateUnfilledOrderQuantity(long orderId, long newQuantity) {
        ensureInitialized();
        
        Order order = unfilledOrdersMap.get(orderId);
        if (order != null) {
            order.setQuantity(newQuantity);
            unfilledOrdersMap.put(orderId, order);
            return true;
        }
        return false;
    }
    
    public ConcurrentHashMap<Long, Long> getOrdersAtPrice(String symbol, long price) {
        ensureInitialized();
        
        ConcurrentHashMap<Long, Long> priceMap = symbolPriceIndex.get(symbol);
        if (priceMap != null) {
            ConcurrentHashMap<Long, Long> result = new ConcurrentHashMap<>();
            Long orderId = priceMap.get(price);
            if (orderId != null) {
                result.put(price, orderId);
            }
            return result;
        }
        return new ConcurrentHashMap<>();
    }
    
    // Pending Trades Management
    public long addPendingTrade(Trade trade) {
        ensureInitialized();
        
        if (trade == null) {
            return -1;
        }
        
        long tradeId = nextTradeId.getAndIncrement();
        trade.setTradeId(tradeId);
        
        try {
            pendingTradesMap.put(tradeId, trade);
            return tradeId;
        } catch (Exception e) {
            logger.error("Failed to add pending trade: {}", tradeId, e);
            return -1;
        }
    }
    
    public Trade getPendingTrade(long tradeId) {
        ensureInitialized();
        return pendingTradesMap.get(tradeId);
    }
    
    public Trade removePendingTrade(long tradeId) {
        ensureInitialized();
        return pendingTradesMap.remove(tradeId);
    }
    
    public boolean updatePendingTradeStatus(long tradeId, String status) {
        ensureInitialized();
        
        Trade trade = pendingTradesMap.get(tradeId);
        if (trade != null) {
            // Note: Trade model doesn't have status field, but method exists for API compatibility
            pendingTradesMap.put(tradeId, trade);
            return true;
        }
        return false;
    }
    
    // Utility Methods
    public long getUnfilledOrdersCount() {
        ensureInitialized();
        return unfilledOrdersMap.size();
    }
    
    public long getPendingTradesCount() {
        ensureInitialized();
        return pendingTradesMap.size();
    }
    
    public long getNextOrderId() {
        return nextOrderId.getAndIncrement();
    }
    
    public long getNextTradeId() {
        return nextTradeId.getAndIncrement();
    }
    
    public void force() {
        // No-op for in-memory implementation
    }
    
    private void ensureInitialized() {
        if (!initialized.get() || closed.get()) {
            throw new IllegalStateException("InMemoryChronicleMapManager is not initialized or has been closed");
        }
    }
    
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            unfilledOrdersMap.clear();
            pendingTradesMap.clear();
            symbolPriceIndex.clear();
            logger.info("InMemoryChronicleMapManager closed");
        }
    }
    
    public boolean isInitialized() {
        return initialized.get();
    }
    
    public boolean isClosed() {
        return closed.get();
    }
}