package quantum.exchange.queue;

import quantum.exchange.memory.MmapOrderBookManager;
import quantum.exchange.memory.SharedMemoryLayout;
import quantum.exchange.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.MappedByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public class TradeResultQueue {
    private static final Logger logger = LoggerFactory.getLogger(TradeResultQueue.class);
    
    private final MmapOrderBookManager memoryManager;
    private final MappedByteBuffer buffer;
    private final AtomicLong localHead = new AtomicLong(0);
    private final AtomicLong localTail = new AtomicLong(0);
    private volatile boolean initialized = false;
    
    public TradeResultQueue(MmapOrderBookManager memoryManager) {
        this.memoryManager = memoryManager;
        this.buffer = memoryManager.getBuffer();
        initialize();
    }
    
    private void initialize() {
        localHead.set(memoryManager.getTradeQueueHead());
        localTail.set(memoryManager.getTradeQueueTail());
        initialized = true;
        logger.info("TradeResultQueue initialized with head={}, tail={}", localHead.get(), localTail.get());
    }
    
    public boolean offer(Trade trade) {
        if (!initialized) {
            return false;
        }
        
        long currentTail = localTail.get();
        long nextTail = (currentTail + 1) % SharedMemoryLayout.TRADE_QUEUE_CAPACITY;
        
        if (nextTail == localHead.get()) {
            return false;
        }
        
        writeTradeToBuffer(trade, currentTail);
        
        localTail.set(nextTail);
        memoryManager.setTradeQueueTail(nextTail);
        
        return true;
    }
    
    public Trade poll() {
        if (!initialized) {
            return null;
        }
        
        long currentHead = localHead.get();
        
        if (currentHead == localTail.get()) {
            return null;
        }
        
        Trade trade = readTradeFromBuffer(currentHead);
        
        long nextHead = (currentHead + 1) % SharedMemoryLayout.TRADE_QUEUE_CAPACITY;
        localHead.set(nextHead);
        memoryManager.setTradeQueueHead(nextHead);
        
        return trade;
    }
    
    public Trade peek() {
        if (!initialized) {
            return null;
        }
        
        long currentHead = localHead.get();
        
        if (currentHead == localTail.get()) {
            return null;
        }
        
        return readTradeFromBuffer(currentHead);
    }
    
    public boolean isEmpty() {
        return localHead.get() == localTail.get();
    }
    
    public boolean isFull() {
        long nextTail = (localTail.get() + 1) % SharedMemoryLayout.TRADE_QUEUE_CAPACITY;
        return nextTail == localHead.get();
    }
    
    public long size() {
        long head = localHead.get();
        long tail = localTail.get();
        
        if (tail >= head) {
            return tail - head;
        } else {
            return (SharedMemoryLayout.TRADE_QUEUE_CAPACITY - head) + tail;
        }
    }
    
    public long capacity() {
        return SharedMemoryLayout.TRADE_QUEUE_CAPACITY - 1;
    }
    
    public double utilizationPercentage() {
        return (double) size() / capacity() * 100.0;
    }
    
    public boolean offerTrade(long buyOrderId, long sellOrderId, long price, long quantity, int symbolHash) {
        long tradeId = memoryManager.incrementAndGetTradeId();
        long timestamp = System.nanoTime();
        
        Trade trade = new Trade(tradeId, buyOrderId, sellOrderId, price, quantity, timestamp, symbolHash);
        return offer(trade);
    }
    
    public Long offerTradeAndGetId(long buyOrderId, long sellOrderId, long price, long quantity, int symbolHash) {
        long tradeId = memoryManager.incrementAndGetTradeId();
        long timestamp = System.nanoTime();
        
        Trade trade = new Trade(tradeId, buyOrderId, sellOrderId, price, quantity, timestamp, symbolHash);
        boolean success = offer(trade);
        return success ? tradeId : null;
    }
    
    private void writeTradeToBuffer(Trade trade, long index) {
        int offset = SharedMemoryLayout.getTradeOffset((int) index);
        buffer.position(offset);
        trade.writeToBuffer(buffer);
    }
    
    private Trade readTradeFromBuffer(long index) {
        int offset = SharedMemoryLayout.getTradeOffset((int) index);
        buffer.position(offset);
        
        Trade trade = new Trade();
        trade.readFromBuffer(buffer);
        return trade;
    }
    
    public void syncWithMemory() {
        if (initialized) {
            localHead.set(memoryManager.getTradeQueueHead());
            localTail.set(memoryManager.getTradeQueueTail());
        }
    }
    
    public void clear() {
        localHead.set(0);
        localTail.set(0);
        memoryManager.setTradeQueueHead(0);
        memoryManager.setTradeQueueTail(0);
        logger.info("TradeResultQueue cleared");
    }
    
    public long getHead() {
        return localHead.get();
    }
    
    public long getTail() {
        return localTail.get();
    }
    
    public long getTradeQueueTail() {
        return localTail.get();
    }
    
    public Trade getTradeAt(long index) {
        if (!initialized) {
            return null;
        }
        
        if (!SharedMemoryLayout.isValidTradeIndex((int) index)) {
            return null;
        }
        
        return readTradeFromBuffer(index);
    }
    
    @Override
    public String toString() {
        return String.format("TradeResultQueue{head=%d, tail=%d, size=%d, capacity=%d, utilization=%.2f%%}",
                getHead(), getTail(), size(), capacity(), utilizationPercentage());
    }
}