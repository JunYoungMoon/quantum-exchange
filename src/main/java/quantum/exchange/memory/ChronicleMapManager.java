package quantum.exchange.memory;

import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;
import quantum.exchange.model.Order;
import quantum.exchange.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ChronicleMapManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ChronicleMapManager.class);
    
    private static final String UNFILLED_ORDERS_FILE = "./data/unfilled-orders.dat";
    private static final String PENDING_TRADES_FILE = "./data/pending-trades.dat";
    private static final long UNFILLED_ORDERS_ENTRIES = 1_000_000;
    private static final long PENDING_TRADES_ENTRIES = 10_000_000;
    
    private ChronicleMap<Long, Order> unfilledOrdersMap;
    private ChronicleMap<Long, Trade> pendingTradesMap;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong nextOrderId = new AtomicLong(1);
    private final AtomicLong nextTradeId = new AtomicLong(1);
    
    // In-memory indexes for fast lookups
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Long>> symbolPriceIndex = new ConcurrentHashMap<>();
    
    public void initialize() throws IOException {
        if (initialized.compareAndSet(false, true)) {
            try {
                createDirectoriesIfNeeded();
                
                // Initialize unfilled orders map
                unfilledOrdersMap = ChronicleMapBuilder
                    .of(Long.class, Order.class)
                    .entries(UNFILLED_ORDERS_ENTRIES)
                    .averageValue(new Order())
                    .createPersistedTo(new File(UNFILLED_ORDERS_FILE));
                
                // Initialize pending trades map
                pendingTradesMap = ChronicleMapBuilder
                    .of(Long.class, Trade.class)
                    .entries(PENDING_TRADES_ENTRIES)
                    .averageValue(new Trade())
                    .createPersistedTo(new File(PENDING_TRADES_FILE));
                
                // Rebuild in-memory indexes from persisted data
                rebuildIndexes();
                
                logger.info("ChronicleMapManager initialized successfully");
                logger.info("Unfilled orders: {} entries", unfilledOrdersMap.size());
                logger.info("Pending trades: {} entries", pendingTradesMap.size());
                
            } catch (Exception e) {
                initialized.set(false);
                cleanup();
                throw new IOException("Failed to initialize ChronicleMapManager", e);
            }
        }
    }
    
    private void createDirectoriesIfNeeded() {
        File dataDir = new File("./data");
        if (!dataDir.exists()) {
            boolean created = dataDir.mkdirs();
            if (!created) {
                logger.warn("Could not create data directory: {}", dataDir.getAbsolutePath());
            }
        }
    }
    
    private void rebuildIndexes() {
        logger.info("Rebuilding indexes from persisted data...");
        
        // Rebuild symbol-price index for unfilled orders
        unfilledOrdersMap.forEach((orderId, order) -> {
            if (order != null && order.getSymbol() != null) {
                symbolPriceIndex
                    .computeIfAbsent(order.getSymbol(), k -> new ConcurrentHashMap<>())
                    .put(order.getPrice(), orderId);
                
                // Update next order ID to avoid conflicts
                long currentOrderId = order.getOrderId();
                nextOrderId.updateAndGet(current -> Math.max(current, currentOrderId + 1));
            }
        });
        
        // Update next trade ID from pending trades
        pendingTradesMap.forEach((tradeId, trade) -> {
            if (trade != null) {
                nextTradeId.updateAndGet(current -> Math.max(current, tradeId + 1));
            }
        });
        
        logger.info("Index rebuild completed. Next order ID: {}, Next trade ID: {}", 
                   nextOrderId.get(), nextTradeId.get());
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
            // Update trade status (you might need to add status field to Trade model)
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
        if (unfilledOrdersMap != null) {
            unfilledOrdersMap.close();
        }
        if (pendingTradesMap != null) {
            pendingTradesMap.close();
        }
    }
    
    private void ensureInitialized() {
        if (!initialized.get() || closed.get()) {
            throw new IllegalStateException("ChronicleMapManager is not initialized or has been closed");
        }
    }
    
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cleanup();
            logger.info("ChronicleMapManager closed");
        }
    }
    
    private void cleanup() {
        try {
            if (unfilledOrdersMap != null) {
                unfilledOrdersMap.close();
                unfilledOrdersMap = null;
            }
            if (pendingTradesMap != null) {
                pendingTradesMap.close();
                pendingTradesMap = null;
            }
            symbolPriceIndex.clear();
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        }
    }
    
    public boolean isInitialized() {
        return initialized.get();
    }
    
    public boolean isClosed() {
        return closed.get();
    }
}