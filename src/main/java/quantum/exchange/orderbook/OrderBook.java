package quantum.exchange.orderbook;

import quantum.exchange.memory.InMemoryChronicleMapManager;
import quantum.exchange.memory.SharedMemoryLayout;
import quantum.exchange.model.Order;
import quantum.exchange.model.OrderSide;
import quantum.exchange.model.PriceLevel;
import quantum.exchange.model.Trade;
import quantum.exchange.queue.TradeResultQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.MappedByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OrderBook {
    private static final Logger logger = LoggerFactory.getLogger(OrderBook.class);
    
    private final String symbol;
    private final int symbolIndex;
    private final int symbolHash;
    private final InMemoryChronicleMapManager chronicleMapManager;
    private final MappedByteBuffer buffer;
    private final TradeResultQueue tradeQueue;
    
    private final TreeMap<Long, PriceLevel> bidLevels = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, PriceLevel> askLevels = new TreeMap<>();
    
    private final Map<Long, List<Order>> bidOrders = new ConcurrentHashMap<>();
    private final Map<Long, List<Order>> askOrders = new ConcurrentHashMap<>();
    
    private volatile long bestBidPrice = 0;
    private volatile long bestAskPrice = Long.MAX_VALUE;
    
    public OrderBook(String symbol, int symbolIndex, InMemoryChronicleMapManager chronicleMapManager, MappedByteBuffer buffer, TradeResultQueue tradeQueue) {
        this.symbol = symbol;
        this.symbolIndex = symbolIndex;
        this.symbolHash = symbol.hashCode();
        this.chronicleMapManager = chronicleMapManager;
        this.buffer = buffer;
        this.tradeQueue = tradeQueue;
        
        logger.info("OrderBook created for symbol: {} (index: {}, hash: {})", symbol, symbolIndex, symbolHash);
    }
    
    public synchronized List<Long> processOrder(Order order) {
        List<Long> executedTrades = new ArrayList<>();
        
        if (order.getType().name().equals("MARKET")) {
            executedTrades.addAll(processMarketOrder(order));
        } else {
            executedTrades.addAll(processLimitOrder(order));
        }
        
        updateBestPrices();
        return executedTrades;
    }
    
    private List<Long> processMarketOrder(Order order) {
        List<Long> trades = new ArrayList<>();
        long remainingQuantity = order.getQuantity();
        List<Long> pricesToRemove = new ArrayList<>();
        
        if (order.getSide() == OrderSide.BUY) {
            // Create a copy of keys to avoid ConcurrentModificationException
            List<Long> askPrices = new ArrayList<>(askLevels.keySet());
            
            for (Long price : askPrices) {
                if (remainingQuantity <= 0) break;
                
                List<Order> ordersAtPrice = askOrders.get(price);
                if (ordersAtPrice == null || ordersAtPrice.isEmpty()) {
                    pricesToRemove.add(price);
                    continue;
                }
                
                remainingQuantity = executeOrdersAtPrice(order, ordersAtPrice, price, remainingQuantity, trades);
                
                if (ordersAtPrice.isEmpty()) {
                    pricesToRemove.add(price);
                }
            }
            
            // Remove empty price levels after iteration
            for (Long price : pricesToRemove) {
                askLevels.remove(price);
                askOrders.remove(price);
            }
        } else {
            // Create a copy of keys to avoid ConcurrentModificationException
            List<Long> bidPrices = new ArrayList<>(bidLevels.keySet());
            
            for (Long price : bidPrices) {
                if (remainingQuantity <= 0) break;
                
                List<Order> ordersAtPrice = bidOrders.get(price);
                if (ordersAtPrice == null || ordersAtPrice.isEmpty()) {
                    pricesToRemove.add(price);
                    continue;
                }
                
                remainingQuantity = executeOrdersAtPrice(order, ordersAtPrice, price, remainingQuantity, trades);
                
                if (ordersAtPrice.isEmpty()) {
                    pricesToRemove.add(price);
                }
            }
            
            // Remove empty price levels after iteration
            for (Long price : pricesToRemove) {
                bidLevels.remove(price);
                bidOrders.remove(price);
            }
        }
        
        return trades;
    }
    
    private List<Long> processLimitOrder(Order order) {
        List<Long> trades = new ArrayList<>();
        long remainingQuantity = order.getQuantity();
        
        if (order.getSide() == OrderSide.BUY) {
            while (remainingQuantity > 0 && !askLevels.isEmpty() && askLevels.firstKey() <= order.getPrice()) {
                long price = askLevels.firstKey();
                List<Order> ordersAtPrice = askOrders.get(price);
                if (ordersAtPrice == null || ordersAtPrice.isEmpty()) {
                    askLevels.remove(price);
                    askOrders.remove(price);
                    continue;
                }
                
                remainingQuantity = executeOrdersAtPrice(order, ordersAtPrice, price, remainingQuantity, trades);
                
                if (ordersAtPrice.isEmpty()) {
                    askLevels.remove(price);
                    askOrders.remove(price);
                }
            }
            
            if (remainingQuantity > 0) {
                addBuyOrder(order, remainingQuantity);
            }
        } else {
            while (remainingQuantity > 0 && !bidLevels.isEmpty() && bidLevels.firstKey() >= order.getPrice()) {
                long price = bidLevels.firstKey();
                List<Order> ordersAtPrice = bidOrders.get(price);
                if (ordersAtPrice == null || ordersAtPrice.isEmpty()) {
                    bidLevels.remove(price);
                    bidOrders.remove(price);
                    continue;
                }
                
                remainingQuantity = executeOrdersAtPrice(order, ordersAtPrice, price, remainingQuantity, trades);
                
                if (ordersAtPrice.isEmpty()) {
                    bidLevels.remove(price);
                    bidOrders.remove(price);
                }
            }
            
            if (remainingQuantity > 0) {
                addSellOrder(order, remainingQuantity);
            }
        }
        
        return trades;
    }
    
    private long executeOrdersAtPrice(Order incomingOrder, List<Order> ordersAtPrice, long price, 
                                      long remainingQuantity, List<Long> trades) {
        Iterator<Order> iterator = ordersAtPrice.iterator();
        
        while (iterator.hasNext() && remainingQuantity > 0) {
            Order resting = iterator.next();
            long tradeQuantity = Math.min(remainingQuantity, resting.getQuantity());
            
            long buyOrderId = incomingOrder.getSide() == OrderSide.BUY ? incomingOrder.getOrderId() : resting.getOrderId();
            long sellOrderId = incomingOrder.getSide() == OrderSide.SELL ? incomingOrder.getOrderId() : resting.getOrderId();
            
            // Create trade and store in Chronicle Map
            Trade trade = new Trade();
            trade.setBuyOrderId(buyOrderId);
            trade.setSellOrderId(sellOrderId);
            trade.setPrice(price);
            trade.setQuantity(tradeQuantity);
            trade.setSymbolHash(symbolHash);
            trade.setTimestamp(System.nanoTime());
            
            long tradeId = chronicleMapManager.addPendingTrade(trade);
            if (tradeId > 0) {
                trades.add(tradeId);
                // Also offer to trade queue for backward compatibility
                tradeQueue.offerTradeAndGetId(buyOrderId, sellOrderId, price, tradeQuantity, symbolHash);
            }
            
            remainingQuantity -= tradeQuantity;
            resting.setQuantity(resting.getQuantity() - tradeQuantity);
            
            if (resting.getQuantity() == 0) {
                iterator.remove();
                // Remove from Chronicle Map as order is fully filled
                chronicleMapManager.removeUnfilledOrder(resting.getOrderId());
            } else {
                // Update quantity in Chronicle Map for partially filled order
                chronicleMapManager.updateUnfilledOrderQuantity(resting.getOrderId(), resting.getQuantity());
            }
            
            updatePriceLevel(price, incomingOrder.getSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY, -tradeQuantity);
        }
        
        return remainingQuantity;
    }
    
    private void addBuyOrder(Order order, long quantity) {
        Order partialOrder = createPartialOrder(order, quantity);
        bidOrders.computeIfAbsent(order.getPrice(), k -> new ArrayList<>()).add(partialOrder);
        
        // Store unfilled order in Chronicle Map
        chronicleMapManager.addUnfilledOrder(partialOrder);
        
        PriceLevel level = bidLevels.computeIfAbsent(order.getPrice(), k -> new PriceLevel(k, 0, 0));
        level.addOrder(quantity);
        
        updatePriceLevelInMemory(order.getPrice(), OrderSide.BUY, level);
    }
    
    private void addSellOrder(Order order, long quantity) {
        Order partialOrder = createPartialOrder(order, quantity);
        askOrders.computeIfAbsent(order.getPrice(), k -> new ArrayList<>()).add(partialOrder);
        
        // Store unfilled order in Chronicle Map
        chronicleMapManager.addUnfilledOrder(partialOrder);
        
        PriceLevel level = askLevels.computeIfAbsent(order.getPrice(), k -> new PriceLevel(k, 0, 0));
        level.addOrder(quantity);
        
        updatePriceLevelInMemory(order.getPrice(), OrderSide.SELL, level);
    }
    
    private Order createPartialOrder(Order original, long quantity) {
        Order partial = new Order();
        partial.setOrderId(original.getOrderId());
        partial.setSymbol(this.symbol); // Use the OrderBook's symbol instead of original's null symbol
        partial.setSide(original.getSide());
        partial.setType(original.getType());
        partial.setPrice(original.getPrice());
        partial.setQuantity(quantity);
        partial.setTimestamp(original.getTimestamp());
        return partial;
    }
    
    private void updatePriceLevel(long price, OrderSide side, long quantityChange) {
        TreeMap<Long, PriceLevel> levels = (side == OrderSide.BUY) ? bidLevels : askLevels;
        PriceLevel level = levels.get(price);
        
        if (level != null) {
            if (quantityChange > 0) {
                level.addOrder(quantityChange);
            } else {
                level.removeOrder(-quantityChange);
            }
            
            if (level.isEmpty()) {
                levels.remove(price);
            }
            
            updatePriceLevelInMemory(price, side, level);
        }
    }
    
    private void updatePriceLevelInMemory(long price, OrderSide side, PriceLevel level) {
        int priceLevelIndex = (int) (price % SharedMemoryLayout.MAX_PRICE_LEVELS);
        int offset;
        
        if (side == OrderSide.BUY) {
            offset = SharedMemoryLayout.getSymbolBidLevelsOffset(symbolIndex) + (priceLevelIndex * PriceLevel.BYTE_SIZE);
        } else {
            offset = SharedMemoryLayout.getSymbolAskLevelsOffset(symbolIndex) + (priceLevelIndex * PriceLevel.BYTE_SIZE);
        }
        
        buffer.position(offset);
        level.writeToBuffer(buffer);
    }
    
    private void updateBestPrices() {
        bestBidPrice = bidLevels.isEmpty() ? 0 : bidLevels.firstKey();
        bestAskPrice = askLevels.isEmpty() ? Long.MAX_VALUE : askLevels.firstKey();
    }
    
    public synchronized long getBestBidPrice() {
        return bestBidPrice;
    }
    
    public synchronized long getBestAskPrice() {
        return bestAskPrice;
    }
    
    public synchronized long getSpread() {
        if (bestBidPrice == 0 || bestAskPrice == Long.MAX_VALUE) {
            return -1;
        }
        return bestAskPrice - bestBidPrice;
    }
    
    public synchronized int getBidLevelsCount() {
        return bidLevels.size();
    }
    
    public synchronized int getAskLevelsCount() {
        return askLevels.size();
    }
    
    public synchronized List<PriceLevel> getTopBidLevels(int count) {
        return bidLevels.values().stream().limit(count).toList();
    }
    
    public synchronized List<PriceLevel> getTopAskLevels(int count) {
        return askLevels.values().stream().limit(count).toList();
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public int getSymbolIndex() {
        return symbolIndex;
    }
    
    public int getSymbolHash() {
        return symbolHash;
    }
    
    @Override
    public String toString() {
        return String.format("OrderBook{symbol='%s', bid=%d, ask=%d, spread=%d, bidLevels=%d, askLevels=%d}",
                symbol, bestBidPrice, bestAskPrice, getSpread(), getBidLevelsCount(), getAskLevelsCount());
    }
}