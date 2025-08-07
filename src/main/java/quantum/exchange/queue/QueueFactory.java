package quantum.exchange.queue;

import lombok.extern.slf4j.Slf4j;
import quantum.exchange.config.QueueConfiguration;
import quantum.exchange.memory.MmapOrderBookManager;

/**
 * Chronicle Queue 인스턴스를 생성하는 팩토리 클래스
 */
@Slf4j
public class QueueFactory {
    
    /**
     * Chronicle 주문 큐를 생성한다.
     */
    public static OrderQueueInterface createOrderQueue(QueueConfiguration config) {
        log.info("Chronicle OrderQueue를 생성합니다. 경로: {}", config.getOrderQueuePath());
        return new ChronicleOrderQueue(config.getOrderQueuePath());
    }
    
    /**
     * Chronicle 거래 결과 큐를 생성한다.
     */
    public static TradeResultQueueInterface createTradeResultQueue(QueueConfiguration config, MmapOrderBookManager memoryManager) {
        log.info("Chronicle TradeResultQueue를 생성합니다. 경로: {}", config.getTradeQueuePath());
        return new ChronicleTradeResultQueue(config.getTradeQueuePath(), memoryManager);
    }
}