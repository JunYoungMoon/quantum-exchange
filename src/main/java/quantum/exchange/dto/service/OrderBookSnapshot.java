package quantum.exchange.dto.service;

import quantum.exchange.model.PriceLevel;

import java.util.List;

public record OrderBookSnapshot(String symbol, List<PriceLevel> bidLevels, List<PriceLevel> askLevels, long bestBid,
                                long bestAsk, long spread, long timestamp) {

    @Override
    public String toString() {
        return String.format("OrderBookSnapshot{symbol='%s', bestBid=%d, bestAsk=%d, spread=%d, bidLevels=%d, askLevels=%d}",
                symbol, bestBid, bestAsk, spread, bidLevels.size(), askLevels.size());
    }
}