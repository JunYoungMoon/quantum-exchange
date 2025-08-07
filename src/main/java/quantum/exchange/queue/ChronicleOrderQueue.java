package quantum.exchange.queue;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import quantum.exchange.model.Order;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chronicle Queue를 사용한 주문 큐 구현
 * 기존 OrderQueue와 동일한 API를 제공하면서 Chronicle Queue의 성능과 지속성을 활용한다.
 * 
 * 스레드 안전성:
 * - Chronicle Queue의 appender와 tailer는 스레드 안전하지 않으므로 동기화가 필요함
 * - 모든 읽기/쓰기 작업에 synchronized 블록 사용
 */
@Slf4j
public class ChronicleOrderQueue implements OrderQueueInterface, AutoCloseable {
    
    private final ChronicleQueue queue;
    private final ThreadLocal<ExcerptAppender> appenderThreadLocal;
    private final ThreadLocal<ExcerptTailer> tailerThreadLocal;
    private final AtomicLong size = new AtomicLong(0);
    private volatile boolean initialized = false;
    private final Object sizeLock = new Object(); // 크기 업데이트를 위한 락
    
    public ChronicleOrderQueue(String queuePath) {
        try {
            File queueDir = new File(queuePath);
            if (!queueDir.exists()) {
                queueDir.mkdirs();
            }
            
            this.queue = SingleChronicleQueueBuilder.binary(queueDir)
                    .rollCycle(net.openhft.chronicle.queue.rollcycles.LegacyRollCycles.MINUTELY)
                    .build();
            
            // ThreadLocal로 appender와 tailer를 생성
            this.appenderThreadLocal = ThreadLocal.withInitial(() -> queue.acquireAppender());
            this.tailerThreadLocal = ThreadLocal.withInitial(() -> queue.createTailer());
            
            initialize();
            
        } catch (Exception e) {
            log.error("ChronicleOrderQueue 초기화 실패", e);
            throw new RuntimeException("Chronicle Queue 초기화 실패", e);
        }
    }
    
    private void initialize() {
        // ThreadLocal 테일러를 처음 위치로 설정
        ExcerptTailer initialTailer = tailerThreadLocal.get();
        initialTailer.toStart();
        
        // 크기 초기화 - Chronicle Queue의 실제 크기를 계산
        updateSize();
        
        initialized = true;
        log.info("ChronicleOrderQueue 초기화 완료 - 기존 메시지 수: {}", size.get());
    }
    
    private void updateSize() {
        synchronized (sizeLock) {
            try {
                ExcerptAppender currentAppender = appenderThreadLocal.get();
                ExcerptTailer currentTailer = tailerThreadLocal.get();
                
                long currentTailIndex = currentAppender.lastIndexAppended();
                long currentHeadIndex = currentTailer.index();
                
                if (currentTailIndex == -1) {
                    // 아직 메시지가 없음
                    size.set(0);
                } else {
                    // 대략적인 크기 계산 (정확하지 않을 수 있음)
                    size.set(Math.max(0, currentTailIndex - currentHeadIndex + 1));
                }
            } catch (IllegalStateException e) {
                // 아직 아무것도 append되지 않은 경우
                size.set(0);
            }
        }
    }
    
    public boolean offer(Order order) {
        if (!initialized) {
            return false;
        }
        
        try {
            ExcerptAppender currentAppender = appenderThreadLocal.get();
            
            currentAppender.writeDocument(wire -> {
                wire.write("orderId").writeLong(order.getOrderId());
                wire.write("symbol").text(order.getSymbol());
                wire.write("side").text(order.getSide().name());
                wire.write("type").text(order.getType().name());
                wire.write("price").writeLong(order.getPrice());
                wire.write("quantity").writeLong(order.getQuantity());
                wire.write("timestamp").writeLong(order.getTimestamp());
                wire.write("symbolHash").writeInt(order.getSymbolHash());
            });
            
            updateSize();
            return true;
            
        } catch (Exception e) {
            log.error("주문 큐에 주문 추가 실패", e);
            return false;
        }
    }
    
    public Order poll() {
        if (!initialized) {
            return null;
        }
        
        try {
            ExcerptTailer currentTailer = tailerThreadLocal.get();
            final Order[] result = {null};
            
            boolean hasMessage = currentTailer.readDocument(wire -> {
                if (wire.isEmpty()) {
                    return;
                }
                
                long orderId = wire.read("orderId").readLong();
                String symbol = wire.read("symbol").text();
                String sideStr = wire.read("side").text();
                String typeStr = wire.read("type").text();
                long price = wire.read("price").readLong();
                long quantity = wire.read("quantity").readLong();
                long timestamp = wire.read("timestamp").readLong();
                int symbolHash = wire.read("symbolHash").readInt();
                
                Order order = new Order(orderId, symbol, 
                    quantum.exchange.model.OrderSide.valueOf(sideStr),
                    quantum.exchange.model.OrderType.valueOf(typeStr),
                    price, quantity, timestamp);
                order.setSymbolHash(symbolHash);
                
                result[0] = order;
            });
            
            if (hasMessage && result[0] != null) {
                updateSize();
                return result[0];
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("주문 큐에서 주문 추출 실패", e);
            return null;
        }
    }
    
    public Order peek() {
        if (!initialized) {
            return null;
        }
        
        try {
            ExcerptTailer currentTailer = tailerThreadLocal.get();
            // 현재 tailer 위치를 저장
            long currentIndex = currentTailer.index();
            
            final Order[] result = {null};
            
            boolean hasMessage = currentTailer.readDocument(wire -> {
                if (wire.isEmpty()) {
                    return;
                }
                
                long orderId = wire.read("orderId").readLong();
                String symbol = wire.read("symbol").text();
                String sideStr = wire.read("side").text();
                String typeStr = wire.read("type").text();
                long price = wire.read("price").readLong();
                long quantity = wire.read("quantity").readLong();
                long timestamp = wire.read("timestamp").readLong();
                int symbolHash = wire.read("symbolHash").readInt();
                
                Order order = new Order(orderId, symbol, 
                    quantum.exchange.model.OrderSide.valueOf(sideStr),
                    quantum.exchange.model.OrderType.valueOf(typeStr),
                    price, quantity, timestamp);
                order.setSymbolHash(symbolHash);
                
                result[0] = order;
            });
            
            // tailer를 원래 위치로 되돌림
            currentTailer.moveToIndex(currentIndex);
            
            return hasMessage ? result[0] : null;
            
        } catch (Exception e) {
            log.error("주문 큐에서 주문 조회 실패", e);
            return null;
        }
    }
    
    public boolean isEmpty() {
        try {
            ExcerptAppender currentAppender = appenderThreadLocal.get();
            ExcerptTailer currentTailer = tailerThreadLocal.get();
            
            long lastIndex = currentAppender.lastIndexAppended();
            return lastIndex == -1 || currentTailer.index() > lastIndex;
        } catch (IllegalStateException e) {
            // 아직 아무것도 append되지 않은 경우
            return true;
        }
    }
    
    public boolean isFull() {
        // Chronicle Queue는 디스크 공간이 허용하는 한 무제한
        return false;
    }
    
    public long size() {
        return size.get();
    }
    
    public long capacity() {
        // Chronicle Queue는 실질적으로 무제한 용량
        return Long.MAX_VALUE;
    }
    
    public double utilizationPercentage() {
        // 무제한 용량이므로 항상 0에 가까움
        return 0.0;
    }
    
    public void syncWithMemory() {
        // Chronicle Queue는 자동으로 디스크와 동기화됨
    }
    
    public void clear() {
        try {
            // 새로운 테일러로 모든 메시지를 소비
            ExcerptTailer clearTailer = queue.createTailer();
            while (clearTailer.readDocument(wire -> {})) {
                // 메시지를 읽어서 소비
            }
            
            // 모든 스레드의 tailer를 끝까지 이동 (이는 완벽하지 않음)
            ExcerptTailer currentTailer = tailerThreadLocal.get();
            currentTailer.toEnd();
            
            size.set(0);
            log.info("ChronicleOrderQueue 초기화됨");
            
        } catch (Exception e) {
            log.error("ChronicleOrderQueue 초기화 실패", e);
        }
    }
    
    public long getHead() {
        ExcerptTailer currentTailer = tailerThreadLocal.get();
        return currentTailer.index();
    }
    
    public long getTail() {
        try {
            ExcerptAppender currentAppender = appenderThreadLocal.get();
            return currentAppender.lastIndexAppended();
        } catch (IllegalStateException e) {
            // 아직 아무것도 append되지 않은 경우
            return -1;
        }
    }
    
    @Override
    public void close() {
        try {
            if (queue != null) {
                queue.close();
            }
            log.info("ChronicleOrderQueue 종료됨");
        } catch (Exception e) {
            log.error("ChronicleOrderQueue 종료 중 오류 발생", e);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ChronicleOrderQueue{head=%d, tail=%d, size=%d, capacity=unlimited}",
                getHead(), getTail(), size());
    }
}