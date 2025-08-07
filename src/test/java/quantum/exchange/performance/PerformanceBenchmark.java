package quantum.exchange.performance;

import quantum.exchange.engine.MatchingEngine;
import quantum.exchange.memory.MmapOrderBookManager;
import quantum.exchange.memory.InMemoryChronicleMapManager;
import quantum.exchange.model.Order;
import quantum.exchange.model.OrderSide;
import quantum.exchange.model.OrderType;
import quantum.exchange.config.QueueConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class PerformanceBenchmark {
    
    private static final String TEST_MMAP_FILE = "./test-data/benchmark.mmap";
    private static final String TEST_SYMBOL = "BTC-USD";
    private static final int WARMUP_ORDERS = 10_000;
    private static final int BENCHMARK_ORDERS = 100_000;
    
    private MmapOrderBookManager memoryManager;
    private InMemoryChronicleMapManager chronicleMapManager;
    private MatchingEngine matchingEngine;
    private Random random;
    
    @BeforeEach
    void setUp() throws Exception {
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("./test-data"));
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("./test-data/orders"));
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("./test-data/trades"));
        
        memoryManager = new MmapOrderBookManager(TEST_MMAP_FILE);
        chronicleMapManager = new InMemoryChronicleMapManager();
        QueueConfiguration queueConfig = new QueueConfiguration();
        queueConfig.setOrderQueuePath("./test-data/orders");
        queueConfig.setTradeQueuePath("./test-data/trades");
        matchingEngine = new MatchingEngine(memoryManager, chronicleMapManager, queueConfig);
        matchingEngine.initialize();
        matchingEngine.start();
        
        random = new Random(42);
        
        Thread.sleep(100);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (matchingEngine != null) {
            matchingEngine.stop();
        }
        if (chronicleMapManager != null) {
            chronicleMapManager.close();
        }
        if (memoryManager != null) {
            memoryManager.close();
        }
        
        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(TEST_MMAP_FILE));
    }
    
    @Test
    void benchmarkOrderProcessingLatency() throws Exception {
        System.out.println("=== Order Processing Latency Benchmark ===");
        
        warmup();
        
        List<Long> latencies = new ArrayList<>();
        AtomicLong orderIdGenerator = new AtomicLong(WARMUP_ORDERS + 1);
        
        for (int i = 0; i < BENCHMARK_ORDERS; i++) {
            Order order = generateRandomOrder(orderIdGenerator.getAndIncrement());
            
            long startTime = System.nanoTime();
            matchingEngine.submitOrder(order);
            
            while (matchingEngine.getOrderQueue().size() > 0) {
                Thread.yield();
            }
            long endTime = System.nanoTime();
            
            latencies.add(endTime - startTime);
            
            if (i % 10000 == 0) {
                System.out.printf("Processed %d orders...%n", i);
            }
        }
        
        analyzeLatencies(latencies);
    }
    
    @Test
    void benchmarkThroughput() throws Exception {
        System.out.println("=== Throughput Benchmark ===");
        
        warmup();
        
        AtomicLong orderIdGenerator = new AtomicLong(WARMUP_ORDERS + 1);
        int numOrders = BENCHMARK_ORDERS;
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < numOrders; i++) {
            Order order = generateRandomOrder(orderIdGenerator.getAndIncrement());
            while (!matchingEngine.submitOrder(order)) {
                Thread.yield();
            }
        }
        
        while (matchingEngine.getOrderQueue().size() > 0) {
            Thread.sleep(1);
        }
        
        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;
        
        double throughputPerSecond = (double) numOrders / (totalTime / 1_000_000_000.0);
        double avgLatencyMicros = (double) totalTime / numOrders / 1_000.0;
        
        System.out.printf("Processed %d orders in %.2f ms%n", numOrders, totalTime / 1_000_000.0);
        System.out.printf("Throughput: %.0f orders/second%n", throughputPerSecond);
        System.out.printf("Average latency: %.2f microseconds%n", avgLatencyMicros);
    }
    
    private void warmup() throws Exception {
        System.out.println("Warming up with " + WARMUP_ORDERS + " orders...");
        
        for (int i = 0; i < WARMUP_ORDERS; i++) {
            Order order = generateRandomOrder(i + 1);
            matchingEngine.submitOrder(order);
        }
        
        while (matchingEngine.getOrderQueue().size() > 0) {
            Thread.sleep(1);
        }
        
        System.out.println("Warmup completed");
    }
    
    private Order generateRandomOrder(long orderId) {
        OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
        OrderType type = random.nextDouble() < 0.8 ? OrderType.LIMIT : OrderType.MARKET;
        
        long basePrice = 50000;
        long price = type == OrderType.MARKET ? 0 : 
                basePrice + (random.nextInt(10000) - 5000);
        
        long quantity = 1 + random.nextInt(100);
        
        return new Order(orderId, TEST_SYMBOL, side, type, price, quantity, System.nanoTime());
    }
    
    private void analyzeLatencies(List<Long> latencies) {
        latencies.sort(Long::compareTo);
        
        int size = latencies.size();
        double mean = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000.0;
        double median = latencies.get(size / 2) / 1_000.0;
        double p95 = latencies.get((int) (size * 0.95)) / 1_000.0;
        double p99 = latencies.get((int) (size * 0.99)) / 1_000.0;
        double p999 = latencies.get((int) (size * 0.999)) / 1_000.0;
        double min = latencies.get(0) / 1_000.0;
        double max = latencies.get(size - 1) / 1_000.0;
        
        System.out.println("Latency Analysis (microseconds):");
        System.out.printf("  Mean:    %.2f μs%n", mean);
        System.out.printf("  Median:  %.2f μs%n", median);
        System.out.printf("  95th:    %.2f μs%n", p95);
        System.out.printf("  99th:    %.2f μs%n", p99);
        System.out.printf("  99.9th:  %.2f μs%n", p999);
        System.out.printf("  Min:     %.2f μs%n", min);
        System.out.printf("  Max:     %.2f μs%n", max);
        
        long subMicrosecond = latencies.stream().mapToLong(Long::longValue).filter(l -> l < 1000).count();
        System.out.printf("  Sub-microsecond: %d (%.1f%%)%n", subMicrosecond, 100.0 * subMicrosecond / size);
    }
}