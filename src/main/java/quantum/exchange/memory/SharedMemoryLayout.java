package quantum.exchange.memory;

import quantum.exchange.model.Order;
import quantum.exchange.model.Trade;
import quantum.exchange.model.MarketData;
import quantum.exchange.model.PriceLevel;

/**
 * 공유 메모리 레이아웃 정의
 * 거래소에서 사용하는 메모리 맵 파일의 구조와 오프셋을 정의한다.
 */
public class SharedMemoryLayout {
    
    // 기본 용량 설정
    public static final int HEADER_SIZE = 64;
    public static final int ORDER_QUEUE_CAPACITY = 1024 * 1024;
    public static final int TRADE_QUEUE_CAPACITY = 1024 * 1024;
    public static final int MAX_PRICE_LEVELS = 10000;
    public static final int MAX_SYMBOLS = 1000;
    
    // 각 영역의 전체 크기
    public static final int ORDER_QUEUE_SIZE = ORDER_QUEUE_CAPACITY * Order.BYTE_SIZE;
    public static final int TRADE_QUEUE_SIZE = TRADE_QUEUE_CAPACITY * Trade.BYTE_SIZE;
    public static final int MARKET_DATA_SIZE = MAX_SYMBOLS * MarketData.BYTE_SIZE;
    public static final int PRICE_LEVELS_SIZE = MAX_SYMBOLS * MAX_PRICE_LEVELS * PriceLevel.BYTE_SIZE * 2; // 매수 + 매도
    
    // 각 영역의 오프셋
    public static final int HEADER_OFFSET = 0;
    public static final int ORDER_QUEUE_OFFSET = HEADER_OFFSET + HEADER_SIZE;
    public static final int TRADE_QUEUE_OFFSET = ORDER_QUEUE_OFFSET + ORDER_QUEUE_SIZE;
    public static final int MARKET_DATA_OFFSET = TRADE_QUEUE_OFFSET + TRADE_QUEUE_SIZE;
    public static final int PRICE_LEVELS_OFFSET = MARKET_DATA_OFFSET + MARKET_DATA_SIZE;
    
    public static final int TOTAL_SIZE = PRICE_LEVELS_OFFSET + PRICE_LEVELS_SIZE;
    
    /**
     * 헤더 영역의 오프셋 정의
     * 시스템 상태 정보와 큐 포인터를 저장한다.
     */
    public static class Header {
        public static final int ORDER_QUEUE_HEAD_OFFSET = 0;   // 주문 큐 헤드 포인터
        public static final int ORDER_QUEUE_TAIL_OFFSET = 8;   // 주문 큐 테일 포인터
        public static final int TRADE_QUEUE_HEAD_OFFSET = 16;  // 거래 큐 헤드 포인터
        public static final int TRADE_QUEUE_TAIL_OFFSET = 24;  // 거래 큐 테일 포인터
        public static final int NEXT_TRADE_ID_OFFSET = 32;     // 다음 거래 ID
        public static final int TIMESTAMP_OFFSET = 40;         // 타임스탬프
        public static final int VERSION_OFFSET = 48;           // 버전 정보
        public static final int STATUS_OFFSET = 56;            // 시스템 상태
    }
    
    public static int getSymbolMarketDataOffset(int symbolIndex) {
        return MARKET_DATA_OFFSET + (symbolIndex * MarketData.BYTE_SIZE);
    }
    
    public static int getSymbolBidLevelsOffset(int symbolIndex) {
        return PRICE_LEVELS_OFFSET + (symbolIndex * MAX_PRICE_LEVELS * PriceLevel.BYTE_SIZE * 2);
    }
    
    public static int getSymbolAskLevelsOffset(int symbolIndex) {
        return getSymbolBidLevelsOffset(symbolIndex) + (MAX_PRICE_LEVELS * PriceLevel.BYTE_SIZE);
    }
    
    public static int getOrderOffset(int index) {
        return ORDER_QUEUE_OFFSET + (index * Order.BYTE_SIZE);
    }
    
    public static int getTradeOffset(int index) {
        return TRADE_QUEUE_OFFSET + (index * Trade.BYTE_SIZE);
    }
    
    public static boolean isValidSymbolIndex(int symbolIndex) {
        return symbolIndex >= 0 && symbolIndex < MAX_SYMBOLS;
    }
    
    public static boolean isValidOrderIndex(int index) {
        return index >= 0 && index < ORDER_QUEUE_CAPACITY;
    }
    
    public static boolean isValidTradeIndex(int index) {
        return index >= 0 && index < TRADE_QUEUE_CAPACITY;
    }
    
    public static boolean isValidPriceLevelIndex(int index) {
        return index >= 0 && index < MAX_PRICE_LEVELS;
    }
    
    /**
     * 메모리 레이아웃 정보를 문자열로 반환한다.
     */
    public static String getLayoutInfo() {
        return String.format("""
            공유 메모리 레이아웃:
            전체 크기: %d 바이트 (%.2f MB)
            헤더: %d 바이트 (오프셋 %d)
            주문 큐: %d 바이트 (오프셋 %d, 용량: %d 개 주문)
            거래 큐: %d 바이트 (오프셋 %d, 용량: %d 개 거래)
            시장 데이터: %d 바이트 (오프셋 %d, 최대 심볼: %d 개)
            가격 레벨: %d 바이트 (오프셋 %d, 심볼당 최대 레벨: %d 개)
            """,
            TOTAL_SIZE, TOTAL_SIZE / (1024.0 * 1024.0),
            HEADER_SIZE, HEADER_OFFSET,
            ORDER_QUEUE_SIZE, ORDER_QUEUE_OFFSET, ORDER_QUEUE_CAPACITY,
            TRADE_QUEUE_SIZE, TRADE_QUEUE_OFFSET, TRADE_QUEUE_CAPACITY,
            MARKET_DATA_SIZE, MARKET_DATA_OFFSET, MAX_SYMBOLS,
            PRICE_LEVELS_SIZE, PRICE_LEVELS_OFFSET, MAX_PRICE_LEVELS
        );
    }
}