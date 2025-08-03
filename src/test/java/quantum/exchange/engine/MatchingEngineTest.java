package quantum.exchange.engine;

import quantum.exchange.memory.MmapOrderBookManager;
import quantum.exchange.model.Order;
import quantum.exchange.model.OrderSide;
import quantum.exchange.model.OrderType;
import quantum.exchange.model.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

public class MatchingEngineTest {
    
    private static final String TEST_MMAP_FILE = "./test-data/engine-test.mmap";
    private static final String TEST_SYMBOL = "BTC/USD";
    
    private MmapOrderBookManager memoryManager;
    private MatchingEngine matchingEngine;
    
    @BeforeEach
    void setUp() throws Exception {
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("./test-data"));
        
        memoryManager = new MmapOrderBookManager(TEST_MMAP_FILE);
        matchingEngine = new MatchingEngine(memoryManager);
        matchingEngine.initialize();
        matchingEngine.start();
        
        Thread.sleep(50);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (matchingEngine != null) {
            matchingEngine.stop();
        }
        if (memoryManager != null) {
            memoryManager.close();
        }
        
        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(TEST_MMAP_FILE));
    }
    
    @Test
    void testBasicLimitOrderMatching() throws Exception {
        Order buyOrder = new Order(1, TEST_SYMBOL, OrderSide.BUY, OrderType.LIMIT, 50000, 10, System.nanoTime());
        Order sellOrder = new Order(2, TEST_SYMBOL, OrderSide.SELL, OrderType.LIMIT, 50000, 5, System.nanoTime());
        
        assertTrue(matchingEngine.submitOrder(buyOrder));
        assertTrue(matchingEngine.submitOrder(sellOrder));
        
        Thread.sleep(100);
        
        assertTrue(matchingEngine.getTradeQueue().size() > 0);
        
        Trade trade = matchingEngine.getTradeQueue().poll();
        assertNotNull(trade);
        assertEquals(1, trade.getBuyOrderId());
        assertEquals(2, trade.getSellOrderId());
        assertEquals(50000, trade.getPrice());
        assertEquals(5, trade.getQuantity());
    }
    
    @Test
    void testMarketOrderExecution() throws Exception {
        Order limitOrder = new Order(1, TEST_SYMBOL, OrderSide.SELL, OrderType.LIMIT, 50000, 10, System.nanoTime());
        Order marketOrder = new Order(2, TEST_SYMBOL, OrderSide.BUY, OrderType.MARKET, 0, 5, System.nanoTime());
        
        assertTrue(matchingEngine.submitOrder(limitOrder));
        assertTrue(matchingEngine.submitOrder(marketOrder));
        
        Thread.sleep(100);
        
        assertTrue(matchingEngine.getTradeQueue().size() > 0);
        
        Trade trade = matchingEngine.getTradeQueue().poll();
        assertNotNull(trade);
        assertEquals(2, trade.getBuyOrderId());
        assertEquals(1, trade.getSellOrderId());
        assertEquals(50000, trade.getPrice());
        assertEquals(5, trade.getQuantity());
    }
    
    @Test
    void testPriceTimePriority() throws Exception {
        Order firstBuy = new Order(1, TEST_SYMBOL, OrderSide.BUY, OrderType.LIMIT, 50000, 5, System.nanoTime());
        Thread.sleep(1);
        Order secondBuy = new Order(2, TEST_SYMBOL, OrderSide.BUY, OrderType.LIMIT, 50000, 5, System.nanoTime());
        Order sellOrder = new Order(3, TEST_SYMBOL, OrderSide.SELL, OrderType.LIMIT, 50000, 5, System.nanoTime());
        
        assertTrue(matchingEngine.submitOrder(firstBuy));
        assertTrue(matchingEngine.submitOrder(secondBuy));
        assertTrue(matchingEngine.submitOrder(sellOrder));
        
        Thread.sleep(100);
        
        Trade trade = matchingEngine.getTradeQueue().poll();
        assertNotNull(trade);
        assertEquals(1, trade.getBuyOrderId());
        assertEquals(3, trade.getSellOrderId());
    }
    
    @Test
    void testPartialFill() throws Exception {
        Order buyOrder = new Order(1, TEST_SYMBOL, OrderSide.BUY, OrderType.LIMIT, 50000, 10, System.nanoTime());
        Order sellOrder = new Order(2, TEST_SYMBOL, OrderSide.SELL, OrderType.LIMIT, 50000, 15, System.nanoTime());
        
        assertTrue(matchingEngine.submitOrder(buyOrder));
        assertTrue(matchingEngine.submitOrder(sellOrder));
        
        Thread.sleep(100);
        
        Trade trade = matchingEngine.getTradeQueue().poll();
        assertNotNull(trade);
        assertEquals(10, trade.getQuantity());
        
        var orderBook = matchingEngine.getOrderBook(TEST_SYMBOL);
        assertNotNull(orderBook);
        assertEquals(50000, orderBook.getBestAskPrice());
    }
    
    @Test
    void testOrderBookState() throws Exception {
        Order buyOrder1 = new Order(1, TEST_SYMBOL, OrderSide.BUY, OrderType.LIMIT, 49000, 10, System.nanoTime());
        Order buyOrder2 = new Order(2, TEST_SYMBOL, OrderSide.BUY, OrderType.LIMIT, 50000, 5, System.nanoTime());
        Order sellOrder1 = new Order(3, TEST_SYMBOL, OrderSide.SELL, OrderType.LIMIT, 51000, 8, System.nanoTime());
        Order sellOrder2 = new Order(4, TEST_SYMBOL, OrderSide.SELL, OrderType.LIMIT, 52000, 12, System.nanoTime());
        
        assertTrue(matchingEngine.submitOrder(buyOrder1));
        assertTrue(matchingEngine.submitOrder(buyOrder2));
        assertTrue(matchingEngine.submitOrder(sellOrder1));
        assertTrue(matchingEngine.submitOrder(sellOrder2));
        
        Thread.sleep(100);
        
        var orderBook = matchingEngine.getOrderBook(TEST_SYMBOL);
        assertNotNull(orderBook);
        
        assertEquals(50000, orderBook.getBestBidPrice());
        assertEquals(51000, orderBook.getBestAskPrice());
        assertEquals(1000, orderBook.getSpread());
        assertEquals(2, orderBook.getBidLevelsCount());
        assertEquals(2, orderBook.getAskLevelsCount());
    }
    
    @Test
    void testEngineStatistics() throws Exception {
        var initialStats = matchingEngine.getStatistics();
        assertTrue(initialStats.isRunning());
        
        Order order = new Order(1, TEST_SYMBOL, OrderSide.BUY, OrderType.LIMIT, 50000, 10, System.nanoTime());
        assertTrue(matchingEngine.submitOrder(order));
        
        // Wait for order to be processed
        int maxWait = 10;
        int waited = 0;
        while (matchingEngine.getStatistics().getProcessedOrders() == 0 && waited < maxWait) {
            Thread.sleep(10);
            waited++;
        }
        
        var stats = matchingEngine.getStatistics();
        assertTrue(stats.getProcessedOrders() > 0, "Should have processed at least one order");
    }
    
    @Test
    void testHighVolumeOrders() throws Exception {
        int numOrders = 1000;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < numOrders; i++) {
            OrderSide side = (i % 2 == 0) ? OrderSide.BUY : OrderSide.SELL;
            long price = 50000 + (i % 100);
            Order order = new Order(i + 1, TEST_SYMBOL, side, OrderType.LIMIT, price, 1, System.nanoTime());
            
            assertTrue(matchingEngine.submitOrder(order));
        }
        
        // Wait for all orders to be processed
        int maxWait = 1000; // 10 seconds max
        int waited = 0;
        while (matchingEngine.getOrderQueue().size() > 0 && waited < maxWait) {
            Thread.sleep(10);
            waited++;
        }
        
        long endTime = System.nanoTime();
        double processingTimeMs = (endTime - startTime) / 1_000_000.0;
        double throughputPerSecond = numOrders / (processingTimeMs / 1000.0);
        
        System.out.printf("Processed %d orders in %.2f ms (%.0f orders/sec)%n", 
                numOrders, processingTimeMs, throughputPerSecond);
        
        assertTrue(throughputPerSecond > 10000, "Throughput should be > 10,000 orders/sec");
    }
}