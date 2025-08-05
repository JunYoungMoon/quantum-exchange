package quantum.exchange.dto.service;

public record ExchangeStatus(boolean running, long processedOrders, long processedTrades,
                             double avgProcessingTimeMicros, long orderQueueSize, long tradeQueueSize, int symbolCount,
                             long timestamp) {

    @Override
    public String toString() {
        return String.format("ExchangeStatus{running=%s, processedOrders=%d, processedTrades=%d, " +
                        "avgProcessingTime=%.2fμs, orderQueueSize=%d, tradeQueueSize=%d, symbolCount=%d}",
                running, processedOrders, processedTrades, avgProcessingTimeMicros,
                orderQueueSize, tradeQueueSize, symbolCount);
    }
}