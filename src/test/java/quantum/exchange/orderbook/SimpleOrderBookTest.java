package quantum.exchange.orderbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quantum.exchange.memory.InMemoryChronicleMapManager;
import quantum.exchange.model.Order;
import quantum.exchange.model.OrderSide;
import quantum.exchange.model.OrderType;
import quantum.exchange.model.PriceLevel;
import quantum.exchange.queue.TradeResultQueueInterface;
import quantum.exchange.queue.ChronicleTradeResultQueue;
import quantum.exchange.memory.MmapOrderBookManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 간단한 호가창 동작 테스트
 */
public class SimpleOrderBookTest {
    
    private OrderBook orderBook;
    private InMemoryChronicleMapManager chronicleMapManager;
    private MmapOrderBookManager memoryManager;
    private TradeResultQueueInterface tradeQueue;
    
    @BeforeEach
    void setUp() throws Exception {
        chronicleMapManager = new InMemoryChronicleMapManager();
        chronicleMapManager.initialize();
        
        memoryManager = new MmapOrderBookManager("./test-data/test.mmap");
        memoryManager.initialize();
        
        tradeQueue = new ChronicleTradeResultQueue("./test-data/trades", memoryManager);
        
        orderBook = new OrderBook("BTC-USD", 0, chronicleMapManager, 
            (java.nio.MappedByteBuffer) memoryManager.getBuffer(), tradeQueue);
    }
    
    @Test
    void testBasicSellOrderAddition() {
        // 매도 주문 추가
        Order sellOrder = new Order(1L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5000L, 10L, System.nanoTime());
        List<Long> trades = orderBook.processOrder(sellOrder);
        
        // 매칭되지 않아야 함 (상대방 주문 없음)
        assertEquals(0, trades.size());
        
        // 호가창에 추가되었는지 확인
        List<PriceLevel> askLevels = orderBook.getTopAskLevels(5);
        System.out.println("Ask levels after adding sell order: " + askLevels.size());
        for (PriceLevel level : askLevels) {
            System.out.println("  Price: " + level.getPrice() + ", Quantity: " + level.getTotalQuantity());
        }
        
        assertTrue(askLevels.size() >= 1);
        assertEquals(5000L, askLevels.get(0).getPrice());
        assertEquals(10L, askLevels.get(0).getTotalQuantity());
    }
    
    @Test
    void testBasicBuyOrderAddition() {
        // 매수 주문 추가
        Order buyOrder = new Order(1L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 4900L, 5L, System.nanoTime());
        List<Long> trades = orderBook.processOrder(buyOrder);
        
        // 매칭되지 않아야 함 (상대방 주문 없음)
        assertEquals(0, trades.size());
        
        // 호가창에 추가되었는지 확인
        List<PriceLevel> bidLevels = orderBook.getTopBidLevels(5);
        System.out.println("Bid levels after adding buy order: " + bidLevels.size());
        for (PriceLevel level : bidLevels) {
            System.out.println("  Price: " + level.getPrice() + ", Quantity: " + level.getTotalQuantity());
        }
        
        assertTrue(bidLevels.size() >= 1);
        assertEquals(4900L, bidLevels.get(0).getPrice());
        assertEquals(5L, bidLevels.get(0).getTotalQuantity());
    }
    
    @Test
    void testSimpleMatching() {
        // 먼저 매도 주문 추가
        Order sellOrder = new Order(1L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5000L, 10L, System.nanoTime());
        orderBook.processOrder(sellOrder);
        
        // 매수 주문으로 매칭
        Order buyOrder = new Order(2L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 5000L, 5L, System.nanoTime());
        List<Long> trades = orderBook.processOrder(buyOrder);
        
        // 거래 실행되어야 함
        assertEquals(1, trades.size());
        
        // 호가창 상태 확인
        List<PriceLevel> askLevels = orderBook.getTopAskLevels(5);
        System.out.println("Ask levels after matching: " + askLevels.size());
        for (PriceLevel level : askLevels) {
            System.out.println("  Price: " + level.getPrice() + ", Quantity: " + level.getTotalQuantity());
        }
        
        assertEquals(1, askLevels.size());
        assertEquals(5000L, askLevels.get(0).getPrice());
        assertEquals(5L, askLevels.get(0).getTotalQuantity()); // 10 - 5 = 5 남음
    }
}