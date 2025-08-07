package quantum.exchange.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Chronicle Queue 설정을 위한 구성 클래스
 */
@Data
@Component
@ConfigurationProperties(prefix = "quantum.exchange.queue")
public class QueueConfiguration {
    
    /**
     * 주문 큐 저장 경로
     */
    private String orderQueuePath = "./data/queues/orders";
    
    /**
     * 거래 결과 큐 저장 경로
     */
    private String tradeQueuePath = "./data/queues/trades";
}