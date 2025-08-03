package quantum.exchange.controller;

import quantum.exchange.model.OrderSide;
import quantum.exchange.model.OrderType;
import quantum.exchange.model.MarketData;
import quantum.exchange.service.ExchangeService;
import quantum.exchange.engine.MatchingEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/exchange")
public class ExchangeController {
    
    @Autowired
    private ExchangeService exchangeService;
    
    @PostMapping("/orders/market")
    public ResponseEntity<?> submitMarketOrder(@RequestBody MarketOrderRequest request) {
        try {
            OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
            Long orderId = exchangeService.submitMarketOrder(request.getSymbol(), side, request.getQuantity());
            
            if (orderId != null) {
                return ResponseEntity.ok(new OrderResponse(orderId, "SUBMITTED"));
            } else {
                return ResponseEntity.badRequest().body(new ErrorResponse("Failed to submit order"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Invalid request: " + e.getMessage()));
        }
    }
    
    @PostMapping("/orders/limit")
    public ResponseEntity<?> submitLimitOrder(@RequestBody LimitOrderRequest request) {
        try {
            OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
            Long orderId = exchangeService.submitLimitOrder(request.getSymbol(), side, request.getPrice(), request.getQuantity());
            
            if (orderId != null) {
                return ResponseEntity.ok(new OrderResponse(orderId, "SUBMITTED"));
            } else {
                return ResponseEntity.badRequest().body(new ErrorResponse("Failed to submit order"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Invalid request: " + e.getMessage()));
        }
    }
    
    @GetMapping("/orderbook/{symbol}")
    public ResponseEntity<?> getOrderBook(@PathVariable String symbol) {
        ExchangeService.OrderBookSnapshot snapshot = exchangeService.getOrderBookSnapshot(symbol);
        if (snapshot != null) {
            return ResponseEntity.ok(snapshot);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/market-data/{symbol}")
    public ResponseEntity<?> getMarketData(@PathVariable String symbol) {
        MarketData marketData = exchangeService.getMarketData(symbol);
        if (marketData != null) {
            return ResponseEntity.ok(marketData);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/market-data")
    public ResponseEntity<Map<String, MarketData>> getAllMarketData() {
        return ResponseEntity.ok(exchangeService.getAllMarketData());
    }
    
    @GetMapping("/status")
    public ResponseEntity<ExchangeService.ExchangeStatus> getExchangeStatus() {
        return ResponseEntity.ok(exchangeService.getExchangeStatus());
    }
    
    @GetMapping("/statistics")
    public ResponseEntity<MatchingEngine.EngineStatistics> getStatistics() {
        return ResponseEntity.ok(exchangeService.getEngineStatistics());
    }
    
    @GetMapping("/symbols")
    public ResponseEntity<List<String>> getAvailableSymbols() {
        return ResponseEntity.ok(exchangeService.getAvailableSymbols());
    }
    
    @PostMapping("/symbols")
    public ResponseEntity<?> addSymbol(@RequestBody AddSymbolRequest request) {
        boolean added = exchangeService.addSymbol(request.getSymbol());
        if (added) {
            return ResponseEntity.ok(new SuccessResponse("Symbol added successfully"));
        } else {
            return ResponseEntity.badRequest().body(new ErrorResponse("Failed to add symbol"));
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        ExchangeService.ExchangeStatus status = exchangeService.getExchangeStatus();
        return ResponseEntity.ok(new HealthResponse("OK", status.isRunning()));
    }
    
    public static class MarketOrderRequest {
        private String symbol;
        private String side;
        private long quantity;
        
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public String getSide() { return side; }
        public void setSide(String side) { this.side = side; }
        public long getQuantity() { return quantity; }
        public void setQuantity(long quantity) { this.quantity = quantity; }
    }
    
    public static class LimitOrderRequest {
        private String symbol;
        private String side;
        private long price;
        private long quantity;
        
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public String getSide() { return side; }
        public void setSide(String side) { this.side = side; }
        public long getPrice() { return price; }
        public void setPrice(long price) { this.price = price; }
        public long getQuantity() { return quantity; }
        public void setQuantity(long quantity) { this.quantity = quantity; }
    }
    
    public static class AddSymbolRequest {
        private String symbol;
        
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
    }
    
    public static class OrderResponse {
        private final Long orderId;
        private final String status;
        
        public OrderResponse(Long orderId, String status) {
            this.orderId = orderId;
            this.status = status;
        }
        
        public Long getOrderId() { return orderId; }
        public String getStatus() { return status; }
    }
    
    public static class ErrorResponse {
        private final String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }
    
    public static class SuccessResponse {
        private final String message;
        
        public SuccessResponse(String message) {
            this.message = message;
        }
        
        public String getMessage() { return message; }
    }
    
    public static class HealthResponse {
        private final String status;
        private final boolean engineRunning;
        
        public HealthResponse(String status, boolean engineRunning) {
            this.status = status;
            this.engineRunning = engineRunning;
        }
        
        public String getStatus() { return status; }
        public boolean isEngineRunning() { return engineRunning; }
    }
}