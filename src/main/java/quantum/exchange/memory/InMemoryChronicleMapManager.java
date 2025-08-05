package quantum.exchange.memory;

import lombok.extern.slf4j.Slf4j;
import quantum.exchange.model.Order;
import quantum.exchange.model.Trade;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트 및 개발을 위한 Chronicle Map 기능의 인메모리 구현체
 * 테스트 중 Chronicle Map의 복잡한 JVM 접근 요구사항을 회피한다.
 */
@Slf4j
public class InMemoryChronicleMapManager implements AutoCloseable {
    
    private final ConcurrentHashMap<Long, Order> unfilledOrdersMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Trade> pendingTradesMap = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong nextOrderId = new AtomicLong(1);
    private final AtomicLong nextTradeId = new AtomicLong(1);
    
    // 빠른 조회를 위한 인메모리 인덱스
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Long>> symbolPriceIndex = new ConcurrentHashMap<>();
    
    public void initialize() throws IOException {
        if (initialized.compareAndSet(false, true)) {
            log.info("인메모리 크로니클 맵 매니저 초기화 성공");
        }
    }
    
    // 미체결 주문 관리
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
            log.error("미체결 주문 추가 실패: {}", order.getOrderId(), e);
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
    
    // 대기 중인 거래 관리
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
            log.error("대기 중인 거래 추가 실패: {}", tradeId, e);
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
            // 참고: Trade 모델에는 상태 필드가 없지만 API 호환성을 위해 메서드 존재
            pendingTradesMap.put(tradeId, trade);
            return true;
        }
        return false;
    }
    
    // 유틸리티 메서드
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
        // 인메모리 구현에서는 아무 작업 없음
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
            log.info("인메모리 크로니클 맵 매니저 종료");
        }
    }
    
    public boolean isInitialized() {
        return initialized.get();
    }
    
    public boolean isClosed() {
        return closed.get();
    }
}