package quantum.exchange.service;

import lombok.extern.slf4j.Slf4j;
import quantum.exchange.dto.service.ExchangeStatus;
import quantum.exchange.dto.service.OrderBookSnapshot;
import quantum.exchange.engine.MatchingEngine;
import quantum.exchange.memory.MmapOrderBookManager;
import quantum.exchange.memory.InMemoryChronicleMapManager;
import quantum.exchange.model.*;
import quantum.exchange.orderbook.OrderBook;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 거래소의 핵심 서비스 클래스
 * 주문 처리, 매칭 엔진 관리, 시장 데이터 제공 등의 기능을 담당한다.
 */
@Slf4j
@Service
public class ExchangeService {
    
    private static final String MMAP_FILE_PATH = "./data/exchange.mmap";
    
    private MmapOrderBookManager memoryManager;
    private InMemoryChronicleMapManager chronicleMapManager;
    private MatchingEngine matchingEngine;
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    
    /**
     * 거래소 서비스를 초기화한다.
     * 메모리 매니저, 크로니클 맵, 매칭 엔진을 설정한다.
     */
    @PostConstruct
    public void initialize() {
        try {
            log.info("거래소 서비스 초기화 시작");
            
            memoryManager = new MmapOrderBookManager(MMAP_FILE_PATH);
            chronicleMapManager = new InMemoryChronicleMapManager();
            matchingEngine = new MatchingEngine(memoryManager, chronicleMapManager);
            
            matchingEngine.initialize();
            matchingEngine.start();
            
            log.info("거래소 서비스 초기화 성공");
        } catch (Exception e) {
            log.error("거래소 서비스 초기화 실패", e);
            throw new RuntimeException("거래소 초기화 실패", e);
        }
    }
    
    /**
     * 거래소 서비스를 종료한다.
     * 모든 리소스를 안전하게 다운한다.
     */
    @PreDestroy
    public void shutdown() {
        try {
            log.info("거래소 서비스 종료 시작");
            
            if (matchingEngine != null) {
                matchingEngine.stop();
                log.debug("매칭 엔진 중지 완료");
            }
            if (chronicleMapManager != null) {
                chronicleMapManager.close();
                log.debug("크로니클 맵 매니저 종료 완료");
            }
            if (memoryManager != null) {
                memoryManager.close();
                log.debug("메모리 매니저 종료 완료");
            }
            
            log.info("거래소 서비스 종료 완료");
        } catch (Exception e) {
            log.error("거래소 서비스 종료 중 오류 발생", e);
        }
    }
    
    public Long submitOrder(String symbol, OrderSide side, OrderType type, long price, long quantity) {
        if (!isValidOrder(symbol, side, type, price, quantity)) {
            return null;
        }
        
        long orderId = orderIdGenerator.getAndIncrement();
        Order order = new Order(orderId, symbol, side, type, price, quantity, System.nanoTime());
        
        boolean submitted = matchingEngine.submitOrder(order);
        return submitted ? orderId : null;
    }
    
    public Long submitMarketOrder(String symbol, OrderSide side, long quantity) {
        return submitOrder(symbol, side, OrderType.MARKET, 0, quantity);
    }

    public Long submitLimitOrder(String symbol, OrderSide side, long price, long quantity) {
        return submitOrder(symbol, side, OrderType.LIMIT, price, quantity);
    }
    
    private boolean isValidOrder(String symbol, OrderSide side, OrderType type, long price, long quantity) {
        if (symbol == null || symbol.trim().isEmpty()) {
            log.warn("잘못된 심볼: {}", symbol);
            return false;
        }
        
        if (side == null) {
            log.warn("잘못된 주문 방향: null");
            return false;
        }
        
        if (type == null) {
            log.warn("잘못된 주문 유형: null");
            return false;
        }
        
        if (quantity <= 0) {
            log.warn("잘못된 수량: {}", quantity);
            return false;
        }
        
        if ((type == OrderType.LIMIT || type == OrderType.MARKET_WITH_PRICE) && price <= 0) {
            log.warn("잘못된 {} 주문 가격: {}", type, price);
            return false;
        }
        
        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        if (orderBook == null) {
            log.warn("알 수 없는 심볼: {}", symbol);
            return false;
        }
        
        return true;
    }
    
    public OrderBookSnapshot getOrderBookSnapshot(String symbol) {
        OrderBook orderBook = matchingEngine.getOrderBook(symbol);
        if (orderBook == null) {
            return null;
        }
        
        List<PriceLevel> bidLevels = orderBook.getTopBidLevels(10);
        List<PriceLevel> askLevels = orderBook.getTopAskLevels(10);
        
        return new OrderBookSnapshot(symbol, bidLevels, askLevels, 
                orderBook.getBestBidPrice(), orderBook.getBestAskPrice(), 
                orderBook.getSpread(), System.nanoTime());
    }
    
    public MarketData getMarketData(String symbol) {
        return matchingEngine.getMarketData(symbol);
    }
    
    public Map<String, MarketData> getAllMarketData() {
        return matchingEngine.getOrderBooks().keySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        symbol -> symbol,
                        symbol -> matchingEngine.getMarketData(symbol)
                ));
    }
    
    public MatchingEngine.EngineStatistics getEngineStatistics() {
        return matchingEngine.getStatistics();
    }
    
    public ExchangeStatus getExchangeStatus() {
        MatchingEngine.EngineStatistics stats = matchingEngine.getStatistics();
        return new ExchangeStatus(
                matchingEngine.isRunning(),
                stats.processedOrders(),
                stats.processedTrades(),
                stats.getLastProcessTimeMicros(),
                stats.orderQueueSize(),
                stats.tradeQueueSize(),
                stats.symbolCount(),
                System.nanoTime()
        );
    }
    
    public List<String> getAvailableSymbols() {
        return List.copyOf(matchingEngine.getOrderBooks().keySet());
    }
    
    public boolean addSymbol(String symbol) {
        return matchingEngine.addSymbol(symbol);
    }
}