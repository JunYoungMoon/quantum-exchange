package quantum.exchange.orderbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quantum.exchange.memory.InMemoryChronicleMapManager;
import quantum.exchange.memory.SharedMemoryLayout;
import quantum.exchange.model.Order;
import quantum.exchange.model.OrderSide;
import quantum.exchange.model.OrderType;
import quantum.exchange.model.PriceLevel;
import quantum.exchange.queue.TradeResultQueue;
import quantum.exchange.memory.MmapOrderBookManager;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 지정가 주문 매칭 로직의 가격-시간 우선순위 테스트
 */
public class LimitOrderMatchingTest {
    
    private OrderBook orderBook;
    private InMemoryChronicleMapManager chronicleMapManager;
    private MmapOrderBookManager memoryManager;
    private TradeResultQueue tradeQueue;
    
    @BeforeEach
    void setUp() throws Exception {
        chronicleMapManager = new InMemoryChronicleMapManager();
        chronicleMapManager.initialize();
        
        memoryManager = new MmapOrderBookManager("./test-data/test.mmap");
        memoryManager.initialize();
        
        tradeQueue = new TradeResultQueue(memoryManager);
        ByteBuffer byteBuffer = memoryManager.getBuffer();
        
        orderBook = new OrderBook("BTC-USD", 0, chronicleMapManager, (java.nio.MappedByteBuffer) byteBuffer, tradeQueue);
    }
    
    @Test
    void testScenario1_BuyOrderMatchesLowestSellPrice() {
        // 준비: 호가창에 매도 주문 추가
        // Sell 10 units at 5000 KRW
        Order sellOrder1 = new Order(1L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5000L, 10L, System.nanoTime());
        orderBook.processOrder(sellOrder1);
        
        // Sell 10 units at 5100 KRW
        Order sellOrder2 = new Order(2L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5100L, 10L, System.nanoTime());
        orderBook.processOrder(sellOrder2);
        
        // 호가창 상태 확인
        List<PriceLevel> askLevels = orderBook.getTopAskLevels(5);
        assertEquals(2, askLevels.size());
        assertEquals(5000L, askLevels.get(0).getPrice()); // 최저가 먼저
        assertEquals(5100L, askLevels.get(1).getPrice());
        
        // 실행: Buy 1 unit at 5100 KRW
        Order buyOrder = new Order(3L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 5100L, 1L, System.nanoTime());
        List<Long> trades = orderBook.processOrder(buyOrder);
        
        // 검증: 1개 거래가 5000 가격에서 실행되어야 함
        assertEquals(1, trades.size());
        
        // 호가창 상태 확인: Sell 9@5000, Sell 10@5100 남아야 함
        askLevels = orderBook.getTopAskLevels(5);
        assertEquals(2, askLevels.size());
        assertEquals(5000L, askLevels.get(0).getPrice());
        assertEquals(9L, askLevels.get(0).getTotalQuantity()); // 9개 남음
        assertEquals(5100L, askLevels.get(1).getPrice());
        assertEquals(10L, askLevels.get(1).getTotalQuantity()); // 10개 그대로
    }
    
    @Test
    void testScenario2_BuyOrderMatchesMultiplePriceLevels() {
        // 준비: 시나리오 1 이후 상태 설정
        // Sell 9 units at 5000 KRW
        Order sellOrder1 = new Order(1L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5000L, 9L, System.nanoTime());
        orderBook.processOrder(sellOrder1);
        
        // Sell 10 units at 5100 KRW
        Order sellOrder2 = new Order(2L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5100L, 10L, System.nanoTime());
        orderBook.processOrder(sellOrder2);
        
        // 실행: Buy 11 units at 5100 KRW
        Order buyOrder = new Order(3L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 5100L, 11L, System.nanoTime());
        List<Long> trades = orderBook.processOrder(buyOrder);
        
        // 검증: 2개 거래가 실행되어야 함
        assertEquals(2, trades.size());
        
        // 호가창 상태 확인: Sell 8@5100만 남아야 함
        List<PriceLevel> askLevels = orderBook.getTopAskLevels(5);
        assertEquals(1, askLevels.size());
        assertEquals(5100L, askLevels.get(0).getPrice());
        assertEquals(8L, askLevels.get(0).getTotalQuantity()); // 8개 남음
    }
    
    @Test
    void testPriceTimePriority_SamePrice() {
        // 준비: 같은 가격에 여러 매도 주문
        Order sellOrder1 = new Order(1L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5000L, 5L, System.nanoTime());
        orderBook.processOrder(sellOrder1);
        
        // 잠시 대기하여 시간 차이 만들기
        try { Thread.sleep(1); } catch (InterruptedException e) { }
        
        Order sellOrder2 = new Order(2L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5000L, 3L, System.nanoTime());
        orderBook.processOrder(sellOrder2);
        
        // 실행: Buy 6 units at 5000 KRW (첫 번째 매도 주문 5개 + 두 번째 매도 주문 1개)
        Order buyOrder = new Order(3L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 5000L, 6L, System.nanoTime());
        List<Long> trades = orderBook.processOrder(buyOrder);
        
        // 검증: 시간 우선순위에 따라 첫 번째 주문이 먼저 체결되어야 함
        assertTrue(trades.size() >= 1);
        
        // 호가창에 2개 남아있어야 함 (두 번째 주문의 일부)
        List<PriceLevel> askLevels = orderBook.getTopAskLevels(5);
        assertEquals(1, askLevels.size());
        assertEquals(5000L, askLevels.get(0).getPrice());
        assertEquals(2L, askLevels.get(0).getTotalQuantity());
    }
    
    @Test
    void testSellOrderMatchingLogic() {
        // 준비: 매수 주문들 추가
        // Buy 10 units at 4900 KRW
        Order buyOrder1 = new Order(1L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 4900L, 10L, System.nanoTime());
        orderBook.processOrder(buyOrder1);
        
        // Buy 10 units at 5000 KRW
        Order buyOrder2 = new Order(2L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 5000L, 10L, System.nanoTime());
        orderBook.processOrder(buyOrder2);
        
        // 호가창 상태 확인 - 매수는 높은 가격부터
        List<PriceLevel> bidLevels = orderBook.getTopBidLevels(5);
        assertEquals(2, bidLevels.size());
        assertEquals(5000L, bidLevels.get(0).getPrice()); // 최고가 먼저
        assertEquals(4900L, bidLevels.get(1).getPrice());
        
        // 실행: Sell 1 unit at 4900 KRW (5000 가격 매수와 매칭되어야 함)
        Order sellOrder = new Order(3L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 4900L, 1L, System.nanoTime());
        List<Long> trades = orderBook.processOrder(sellOrder);
        
        // 검증: 1개 거래가 5000 가격에서 실행되어야 함
        assertEquals(1, trades.size());
        
        // 호가창 상태 확인: Buy 9@5000, Buy 10@4900 남아야 함
        bidLevels = orderBook.getTopBidLevels(5);
        assertEquals(2, bidLevels.size());
        assertEquals(5000L, bidLevels.get(0).getPrice());
        assertEquals(9L, bidLevels.get(0).getTotalQuantity()); // 9개 남음
        assertEquals(4900L, bidLevels.get(1).getPrice());
        assertEquals(10L, bidLevels.get(1).getTotalQuantity()); // 10개 그대로
    }
}