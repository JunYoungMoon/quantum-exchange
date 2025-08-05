package quantum.exchange.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import quantum.exchange.dto.request.AddSymbolRequest;
import quantum.exchange.dto.request.LimitOrderRequest;
import quantum.exchange.dto.request.MarketOrderRequest;
import quantum.exchange.dto.response.ErrorResponse;
import quantum.exchange.dto.response.HealthResponse;
import quantum.exchange.dto.response.OrderResponse;
import quantum.exchange.dto.response.SuccessResponse;
import quantum.exchange.dto.service.ExchangeStatus;
import quantum.exchange.model.OrderSide;
import quantum.exchange.model.MarketData;
import quantum.exchange.service.ExchangeService;
import quantum.exchange.engine.MatchingEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 거래소 REST API 컨트롤러
 * 주문 처리, 시장 데이터 조회, 시스템 상태 확인 등의 기능을 제공한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/exchange")
@RequiredArgsConstructor
public class ExchangeController {
    
    private final ExchangeService exchangeService;
    
    /**
     * 시장가 주문을 제출한다.
     * 일반적인 시장가 주문은 가격 정보가 필요하지 않으며, 현재 시장 가격으로 즉시 실행된다.
     * 선택적으로 가격 제한을 포함할 수 있다.
     */
    @PostMapping("/orders/market")
    public ResponseEntity<?> submitMarketOrder(@RequestBody MarketOrderRequest request) {
        log.debug("시장가 주문 요청 수신: {}", request);
        try {
            OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
            Long orderId;

            // 표준 시장가 주문 (가격 정보 불필요)
            log.debug("표준 시장가 주문 처리: 심볼={}, 방향={}, 수량={}",
                request.getSymbol(), side, request.getQuantity());
            orderId = exchangeService.submitMarketOrder(request.getSymbol(), side, request.getQuantity());

            if (orderId != null) {
                log.info("시장가 주문 성공적으로 제출됨: 주문ID={}", orderId);
                return ResponseEntity.ok(new OrderResponse(orderId, "SUBMITTED"));
            } else {
                log.warn("시장가 주문 제출 실패: {}", request);
                return ResponseEntity.badRequest().body(new ErrorResponse("주문 제출에 실패했습니다"));
            }
        } catch (Exception e) {
            log.error("시장가 주문 처리 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new ErrorResponse("잘못된 요청: " + e.getMessage()));
        }
    }
    
    /**
     * 지정가 주문을 제출한다.
     */
    @PostMapping("/orders/limit")
    public ResponseEntity<?> submitLimitOrder(@RequestBody LimitOrderRequest request) {
        log.debug("지정가 주문 요청 수신: {}", request);
        try {
            OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
            log.debug("지정가 주문 처리: 심볼={}, 방향={}, 가격={}, 수량={}", 
                request.getSymbol(), side, request.getPrice(), request.getQuantity());
            
            Long orderId = exchangeService.submitLimitOrder(request.getSymbol(), side, request.getPrice(), request.getQuantity());
            
            if (orderId != null) {
                log.info("지정가 주문 성공적으로 제출됨: 주문ID={}", orderId);
                return ResponseEntity.ok(new OrderResponse(orderId, "SUBMITTED"));
            } else {
                log.warn("지정가 주문 제출 실패: {}", request);
                return ResponseEntity.badRequest().body(new ErrorResponse("주문 제출에 실패했습니다"));
            }
        } catch (Exception e) {
            log.error("지정가 주문 처리 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new ErrorResponse("잘못된 요청: " + e.getMessage()));
        }
    }
    
    /**
     * 특정 심볼의 호가창 정보를 조회한다.
     */
    @GetMapping("/orderbook/{symbol}")
    public ResponseEntity<?> getOrderBook(@PathVariable String symbol) {
        log.debug("호가창 조회 요청: 심볼={}", symbol);
        var snapshot = exchangeService.getOrderBookSnapshot(symbol);
        if (snapshot != null) {
            log.debug("호가창 조회 성공: 심볼={}", symbol);
            return ResponseEntity.ok(snapshot);
        } else {
            log.warn("호가창을 찾을 수 없음: 심볼={}", symbol);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 특정 심볼의 시장 데이터를 조회한다.
     */
    @GetMapping("/market-data/{symbol}")
    public ResponseEntity<?> getMarketData(@PathVariable String symbol) {
        log.debug("시장 데이터 조회 요청: 심볼={}", symbol);
        MarketData marketData = exchangeService.getMarketData(symbol);
        if (marketData != null) {
            log.debug("시장 데이터 조회 성공: 심볼={}", symbol);
            return ResponseEntity.ok(marketData);
        } else {
            log.warn("시장 데이터를 찾을 수 없음: 심볼={}", symbol);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 모든 심볼의 시장 데이터를 조회한다.
     */
    @GetMapping("/market-data")
    public ResponseEntity<Map<String, MarketData>> getAllMarketData() {
        log.debug("전체 시장 데이터 조회 요청");
        var marketData = exchangeService.getAllMarketData();
        log.debug("전체 시장 데이터 조회 완료: {} 개 심볼", marketData.size());
        return ResponseEntity.ok(marketData);
    }
    
    /**
     * 거래소 엔진의 상태 정보를 조회한다.
     */
    @GetMapping("/status")
    public ResponseEntity<ExchangeStatus> getExchangeStatus() {
        log.debug("거래소 상태 조회 요청");
        var status = exchangeService.getExchangeStatus();
        log.debug("거래소 상태 조회 완료: 실행중={}", status.running());
        return ResponseEntity.ok(status);
    }
    
    /**
     * 거래소 엔진의 통계 정보를 조회한다.
     */
    @GetMapping("/statistics")
    public ResponseEntity<MatchingEngine.EngineStatistics> getStatistics() {
        log.debug("거래소 통계 조회 요청");
        var statistics = exchangeService.getEngineStatistics();
        log.debug("거래소 통계 조회 완료: 처리된 주문={}", statistics.processedOrders());
        return ResponseEntity.ok(statistics);
    }
    
    /**
     * 사용 가능한 거래 심볼 목록을 조회한다.
     */
    @GetMapping("/symbols")
    public ResponseEntity<List<String>> getAvailableSymbols() {
        log.debug("사용 가능한 심볼 목록 조회 요청");
        var symbols = exchangeService.getAvailableSymbols();
        log.debug("사용 가능한 심볼 목록 조회 완료: {} 개 심볼", symbols.size());
        return ResponseEntity.ok(symbols);
    }
    
    /**
     * 새로운 거래 심볼을 추가한다.
     */
    @PostMapping("/symbols")
    public ResponseEntity<?> addSymbol(@RequestBody AddSymbolRequest request) {
        log.debug("심볼 추가 요청: {}", request.getSymbol());
        boolean added = exchangeService.addSymbol(request.getSymbol());
        if (added) {
            log.info("심볼 성공적으로 추가됨: {}", request.getSymbol());
            return ResponseEntity.ok(new SuccessResponse("심볼이 성공적으로 추가되었습니다"));
        } else {
            log.warn("심볼 추가 실패: {}", request.getSymbol());
            return ResponseEntity.badRequest().body(new ErrorResponse("심볼 추가에 실패했습니다"));
        }
    }
    
    /**
     * 거래소 시스템의 건강 상태를 확인한다.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        log.debug("시스템 건강 상태 확인 요청");
        var status = exchangeService.getExchangeStatus();
        boolean isHealthy = status.running();
        log.debug("시스템 건강 상태 확인 완료: 건강={}", isHealthy);
        return ResponseEntity.ok(new HealthResponse("OK", isHealthy));
    }
}