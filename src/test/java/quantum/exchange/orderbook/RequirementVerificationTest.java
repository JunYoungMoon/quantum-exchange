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
 * 요구사항에 명시된 정확한 시나리오를 검증하는 테스트
 * 
 * 시나리오 1: 호가창에 Sell 10@5000, Sell 10@5100이 있을 때
 *           Buy 1@5100 주문 → 1 buy와 1 sell이 매칭
 *           남은 호가: Sell 9@5000, Sell 10@5100
 *
 * 시나리오 2: 그 다음 Buy 11@5100 주문 → 
 *           첫 번째 매칭: Sell 9@5000과 Buy 9@5000
 *           두 번째 매칭: Sell 2@5100과 Buy 2@5100  
 *           남은 호가: Sell 8@5100
 */
public class RequirementVerificationTest {
    
    private OrderBook orderBook;
    private InMemoryChronicleMapManager chronicleMapManager;
    private MmapOrderBookManager memoryManager;
    private TradeResultQueueInterface tradeQueue;
    
    @BeforeEach
    void setUp() throws Exception {
        chronicleMapManager = new InMemoryChronicleMapManager();
        chronicleMapManager.initialize();
        
        memoryManager = new MmapOrderBookManager("./test-data/requirement-test.mmap");
        memoryManager.initialize();
        
        tradeQueue = new ChronicleTradeResultQueue("./test-data/trades", memoryManager);
        
        orderBook = new OrderBook("BTC-USD", 0, chronicleMapManager, 
            (java.nio.MappedByteBuffer) memoryManager.getBuffer(), tradeQueue);
    }
    
    @Test
    void testScenario1_ExactRequirementMatch() {
        // 준비: 호가창에 Sell 10 units at 5000 KRW, Sell 10 units at 5100 KRW
        Order sellOrder1 = new Order(1L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5000L, 10L, System.nanoTime());
        orderBook.processOrder(sellOrder1);
        
        Order sellOrder2 = new Order(2L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5100L, 10L, System.nanoTime());
        orderBook.processOrder(sellOrder2);
        
        // 초기 호가창 상태 확인
        List<PriceLevel> askLevels = orderBook.getTopAskLevels(5);
        assertEquals(2, askLevels.size());
        assertEquals(5000L, askLevels.get(0).getPrice());
        assertEquals(10L, askLevels.get(0).getTotalQuantity());
        assertEquals(5100L, askLevels.get(1).getPrice());
        assertEquals(10L, askLevels.get(1).getTotalQuantity());
        
        // 실행: Buy 1 unit at 5100 KRW 주문
        Order buyOrder = new Order(3L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 5100L, 1L, System.nanoTime());
        List<Long> trades = orderBook.processOrder(buyOrder);
        
        // 검증: 1 buy와 1 sell이 매칭되어야 함
        assertEquals(1, trades.size(), "1개의 거래가 실행되어야 함");
        
        // 검증: 호가창 상태 - Sell 9@5000, Sell 10@5100 남아야 함
        askLevels = orderBook.getTopAskLevels(5);
        assertEquals(2, askLevels.size(), "2개 가격 레벨이 남아있어야 함");
        
        // 첫 번째 레벨: Sell 9@5000
        assertEquals(5000L, askLevels.get(0).getPrice(), "첫 번째 레벨은 5000 가격이어야 함");
        assertEquals(9L, askLevels.get(0).getTotalQuantity(), "첫 번째 레벨은 9개 수량이어야 함");
        assertEquals(1L, askLevels.get(0).getOrderCount(), "첫 번째 레벨은 1개 주문이어야 함");
        
        // 두 번째 레벨: Sell 10@5100
        assertEquals(5100L, askLevels.get(1).getPrice(), "두 번째 레벨은 5100 가격이어야 함");
        assertEquals(10L, askLevels.get(1).getTotalQuantity(), "두 번째 레벨은 10개 수량이어야 함");
        assertEquals(1L, askLevels.get(1).getOrderCount(), "두 번째 레벨은 1개 주문이어야 함");
        
        System.out.println("✅ 시나리오 1 검증 완료:");
        System.out.println("   - 1개 거래 실행됨");
        System.out.println("   - 남은 호가: Sell 9@5000, Sell 10@5100");
    }
    
    @Test
    void testScenario2_ExactRequirementMatch() {
        // 준비: 시나리오 1의 결과 상태 - Sell 9@5000, Sell 10@5100
        Order sellOrder1 = new Order(1L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5000L, 9L, System.nanoTime());
        orderBook.processOrder(sellOrder1);
        
        Order sellOrder2 = new Order(2L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5100L, 10L, System.nanoTime());
        orderBook.processOrder(sellOrder2);
        
        // 실행: Buy 11 units at 5100 KRW 주문
        Order buyOrder = new Order(3L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 5100L, 11L, System.nanoTime());
        List<Long> trades = orderBook.processOrder(buyOrder);
        
        // 검증: 2개 거래가 실행되어야 함
        assertEquals(2, trades.size(), "2개의 거래가 실행되어야 함");
        
        // 검증: 호가창 상태 - Sell 8@5100만 남아야 함
        List<PriceLevel> askLevels = orderBook.getTopAskLevels(5);
        assertEquals(1, askLevels.size(), "1개 가격 레벨만 남아있어야 함");
        
        // 남은 레벨: Sell 8@5100
        assertEquals(5100L, askLevels.get(0).getPrice(), "남은 레벨은 5100 가격이어야 함");
        assertEquals(8L, askLevels.get(0).getTotalQuantity(), "남은 레벨은 8개 수량이어야 함");
        assertEquals(1L, askLevels.get(0).getOrderCount(), "남은 레벨은 1개 주문이어야 함");
        
        System.out.println("✅ 시나리오 2 검증 완료:");
        System.out.println("   - 첫 번째 매칭: Sell 9@5000과 Buy 9@5000");
        System.out.println("   - 두 번째 매칭: Sell 2@5100과 Buy 2@5100");
        System.out.println("   - 남은 호가: Sell 8@5100");
    }
    
    @Test
    void testFullScenarioSequence() {
        // 전체 시나리오를 연속으로 실행하여 검증
        
        // 1단계: 초기 호가창 설정
        Order sellOrder1 = new Order(1L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5000L, 10L, System.nanoTime());
        orderBook.processOrder(sellOrder1);
        
        Order sellOrder2 = new Order(2L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5100L, 10L, System.nanoTime());
        orderBook.processOrder(sellOrder2);
        
        // 2단계: 시나리오 1 - Buy 1@5100
        Order buyOrder1 = new Order(3L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 5100L, 1L, System.nanoTime());
        List<Long> trades1 = orderBook.processOrder(buyOrder1);
        assertEquals(1, trades1.size());
        
        // 중간 상태 확인
        List<PriceLevel> askLevels = orderBook.getTopAskLevels(5);
        assertEquals(2, askLevels.size());
        assertEquals(9L, askLevels.get(0).getTotalQuantity()); // 5000 가격에 9개
        assertEquals(10L, askLevels.get(1).getTotalQuantity()); // 5100 가격에 10개
        
        // 3단계: 시나리오 2 - Buy 11@5100
        Order buyOrder2 = new Order(4L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 5100L, 11L, System.nanoTime());
        List<Long> trades2 = orderBook.processOrder(buyOrder2);
        assertEquals(2, trades2.size());
        
        // 최종 상태 확인
        askLevels = orderBook.getTopAskLevels(5);
        assertEquals(1, askLevels.size());
        assertEquals(5100L, askLevels.get(0).getPrice());
        assertEquals(8L, askLevels.get(0).getTotalQuantity());
        
        System.out.println("✅ 전체 시나리오 시퀀스 검증 완료");
        System.out.println("   - 총 거래 수: " + (trades1.size() + trades2.size()));
        System.out.println("   - 최종 호가창: Sell 8@5100");
    }
    
    @Test 
    void testPriceTimePriorityCorrectness() {
        // 가격-시간 우선순위가 올바르게 작동하는지 검증
        
        // 다양한 가격의 매도 주문들을 추가
        Order sell5200 = new Order(1L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5200L, 5L, System.nanoTime());
        orderBook.processOrder(sell5200);
        
        Order sell5000 = new Order(2L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5000L, 8L, System.nanoTime());
        orderBook.processOrder(sell5000);
        
        Order sell5100 = new Order(3L, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, 5100L, 3L, System.nanoTime());
        orderBook.processOrder(sell5100);
        
        // 호가창이 가격 순으로 정렬되었는지 확인
        List<PriceLevel> askLevels = orderBook.getTopAskLevels(5);
        assertEquals(3, askLevels.size());
        assertEquals(5000L, askLevels.get(0).getPrice(), "가장 낮은 가격이 첫 번째여야 함");
        assertEquals(5100L, askLevels.get(1).getPrice(), "두 번째로 낮은 가격이 두 번째여야 함");
        assertEquals(5200L, askLevels.get(2).getPrice(), "가장 높은 가격이 마지막이어야 함");
        
        // 매수 주문으로 가격 우선순위 테스트
        Order buyOrder = new Order(4L, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 5150L, 12L, System.nanoTime());
        List<Long> trades = orderBook.processOrder(buyOrder);
        
        // 5000과 5100 가격대에서 거래가 실행되어야 함
        assertEquals(2, trades.size(), "2개 거래가 실행되어야 함");
        
        // 5200 가격대만 남아있어야 함
        askLevels = orderBook.getTopAskLevels(5);
        assertEquals(1, askLevels.size());
        assertEquals(5200L, askLevels.get(0).getPrice());
        assertEquals(5L, askLevels.get(0).getTotalQuantity());
        
        System.out.println("✅ 가격-시간 우선순위 검증 완료");
        System.out.println("   - 낮은 가격부터 우선 매칭됨");
        System.out.println("   - 12개 주문이 5000(8개) + 5100(3개) + 부분체결(1개)로 처리됨");
    }
}