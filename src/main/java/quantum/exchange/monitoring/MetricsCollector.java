package quantum.exchange.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import quantum.exchange.engine.MatchingEngine;
import quantum.exchange.service.ExchangeService;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 거래소 성능 지표를 주기적으로 수집하고 모니터링하는 컴포넌트
 * 처리량, 대기시간, 큐 크기 등의 중요한 메트릭을 추적한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsCollector {
    
    private final ExchangeService exchangeService;
    
    private ScheduledExecutorService scheduler;
    private final AtomicLong metricsCollectionCount = new AtomicLong(0);
    
    private volatile long lastProcessedOrders = 0;
    private volatile long lastProcessedTrades = 0;
    private volatile long lastCollectionTime = System.nanoTime();
    
    /**
     * 메트릭 수집기를 시작한다.
     * 5초마다 성능 지표를 수집한다.
     */
    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MetricsCollector");
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleAtFixedRate(this::collectMetrics, 1, 5, TimeUnit.SECONDS);
        log.info("메트릭 수집기 시작 - 5초마다 성능 지표 수집");
    }
    
    /**
     * 메트릭 수집기를 중지한다.
     */
    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("메트릭 수집기 중지됨");
    }
    
    /**
     * 성능 지표를 수집하고 처리량을 계산한다.
     */
    private void collectMetrics() {
        try {
            MatchingEngine.EngineStatistics stats = exchangeService.getEngineStatistics();
            long currentTime = System.nanoTime();
            long timeDeltaMs = (currentTime - lastCollectionTime) / 1_000_000;
            
            long ordersDelta = stats.processedOrders() - lastProcessedOrders;
            long tradesDelta = stats.processedTrades() - lastProcessedTrades;
            
            // 초당 처리량 계산
            double orderThroughput = timeDeltaMs > 0 ? (double) ordersDelta / timeDeltaMs * 1000 : 0;
            double tradeThroughput = timeDeltaMs > 0 ? (double) tradesDelta / timeDeltaMs * 1000 : 0;
            
            logMetrics(stats, orderThroughput, tradeThroughput);
            
            // 다음 비교를 위해 현재 값 저장
            lastProcessedOrders = stats.processedOrders();
            lastProcessedTrades = stats.processedTrades();
            lastCollectionTime = currentTime;
            metricsCollectionCount.incrementAndGet();
            
        } catch (Exception e) {
            log.error("메트릭 수집 중 오류 발생", e);
        }
    }
    
    /**
     * 수집된 메트릭을 로그로 출력하고 성능 경고를 확인한다.
     */
    private void logMetrics(MatchingEngine.EngineStatistics stats, double orderThroughput, double tradeThroughput) {
        log.info("=== 거래소 성능 지표 ===");
        log.info("엔진 상태: {}", stats.running() ? "실행중" : "중지됨");
        log.info("총 처리된 주문: {}", stats.processedOrders());
        log.info("총 실행된 거래: {}", stats.processedTrades());
        log.info("주문 처리량: {:.0f} 주문/초", orderThroughput);
        log.info("거래 처리량: {:.0f} 거래/초", tradeThroughput);
        log.info("마지막 처리 시간: {:.2f} μs", stats.getLastProcessTimeMicros());
        log.info("주문 큐 크기: {}", stats.orderQueueSize());
        log.info("거래 큐 크기: {}", stats.tradeQueueSize());
        log.info("활성 심볼: {}", stats.symbolCount());
        log.info("====================");
        
        // 성능 경고 확인
        if (stats.orderQueueSize() > 10000) {
            log.warn("높은 주문 큐 크기: {} - 잠재적 병목현상", stats.orderQueueSize());
        }
        
        if (stats.getLastProcessTimeMicros() > 100) {
            log.warn("높은 처리 지연시간: {:.2f} μs", stats.getLastProcessTimeMicros());
        }
    }
    
    /**
     * 메트릭 수집 횟수를 반환한다.
     */
    public long getMetricsCollectionCount() {
        return metricsCollectionCount.get();
    }
}