package quantum.exchange.monitoring;

import quantum.exchange.engine.MatchingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);
    
    @Autowired
    private quantum.exchange.service.ExchangeService exchangeService;
    
    private ScheduledExecutorService scheduler;
    private final AtomicLong metricsCollectionCount = new AtomicLong(0);
    
    private volatile long lastProcessedOrders = 0;
    private volatile long lastProcessedTrades = 0;
    private volatile long lastCollectionTime = System.nanoTime();
    
    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MetricsCollector");
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleAtFixedRate(this::collectMetrics, 1, 5, TimeUnit.SECONDS);
        logger.info("MetricsCollector started - collecting metrics every 5 seconds");
    }
    
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
        logger.info("MetricsCollector stopped");
    }
    
    private void collectMetrics() {
        try {
            MatchingEngine.EngineStatistics stats = exchangeService.getEngineStatistics();
            long currentTime = System.nanoTime();
            long timeDeltaMs = (currentTime - lastCollectionTime) / 1_000_000;
            
            long ordersDelta = stats.getProcessedOrders() - lastProcessedOrders;
            long tradesDelta = stats.getProcessedTrades() - lastProcessedTrades;
            
            double orderThroughput = timeDeltaMs > 0 ? (double) ordersDelta / timeDeltaMs * 1000 : 0;
            double tradeThroughput = timeDeltaMs > 0 ? (double) tradesDelta / timeDeltaMs * 1000 : 0;
            
            logMetrics(stats, orderThroughput, tradeThroughput);
            
            lastProcessedOrders = stats.getProcessedOrders();
            lastProcessedTrades = stats.getProcessedTrades();
            lastCollectionTime = currentTime;
            metricsCollectionCount.incrementAndGet();
            
        } catch (Exception e) {
            logger.error("Error collecting metrics", e);
        }
    }
    
    private void logMetrics(MatchingEngine.EngineStatistics stats, double orderThroughput, double tradeThroughput) {
        logger.info("=== Exchange Metrics ===");
        logger.info("Engine Status: {}", stats.isRunning() ? "RUNNING" : "STOPPED");
        logger.info("Total Orders Processed: {}", stats.getProcessedOrders());
        logger.info("Total Trades Executed: {}", stats.getProcessedTrades());
        logger.info("Order Throughput: {:.0f} orders/sec", orderThroughput);
        logger.info("Trade Throughput: {:.0f} trades/sec", tradeThroughput);
        logger.info("Last Processing Time: {:.2f} μs", stats.getLastProcessTimeMicros());
        logger.info("Order Queue Size: {}", stats.getOrderQueueSize());
        logger.info("Trade Queue Size: {}", stats.getTradeQueueSize());
        logger.info("Active Symbols: {}", stats.getSymbolCount());
        logger.info("========================");
        
        if (stats.getOrderQueueSize() > 10000) {
            logger.warn("HIGH ORDER QUEUE SIZE: {} - potential bottleneck", stats.getOrderQueueSize());
        }
        
        if (stats.getLastProcessTimeMicros() > 100) {
            logger.warn("HIGH PROCESSING LATENCY: {:.2f} μs", stats.getLastProcessTimeMicros());
        }
    }
    
    public long getMetricsCollectionCount() {
        return metricsCollectionCount.get();
    }
}