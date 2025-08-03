package quantum.exchange.queue;

import quantum.exchange.memory.MmapOrderBookManager;
import quantum.exchange.memory.SharedMemoryLayout;
import quantum.exchange.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.MappedByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public class OrderQueue {
    private static final Logger logger = LoggerFactory.getLogger(OrderQueue.class);
    
    private final MmapOrderBookManager memoryManager;
    private final MappedByteBuffer buffer;
    private final AtomicLong localHead = new AtomicLong(0);
    private final AtomicLong localTail = new AtomicLong(0);
    private volatile boolean initialized = false;
    
    public OrderQueue(MmapOrderBookManager memoryManager) {
        this.memoryManager = memoryManager;
        this.buffer = memoryManager.getBuffer();
        initialize();
    }
    
    private void initialize() {
        localHead.set(memoryManager.getOrderQueueHead());
        localTail.set(memoryManager.getOrderQueueTail());
        initialized = true;
        logger.info("OrderQueue initialized with head={}, tail={}", localHead.get(), localTail.get());
    }
    
    public boolean offer(Order order) {
        if (!initialized) {
            return false;
        }
        
        long currentTail = localTail.get();
        long nextTail = (currentTail + 1) % SharedMemoryLayout.ORDER_QUEUE_CAPACITY;
        
        if (nextTail == localHead.get()) {
            return false;
        }
        
        writeOrderToBuffer(order, currentTail);
        
        localTail.set(nextTail);
        memoryManager.setOrderQueueTail(nextTail);
        
        return true;
    }
    
    public Order poll() {
        return pollWithSkipCount(0);
    }
    
    private Order pollWithSkipCount(int skipCount) {
        if (!initialized || skipCount > 100) { // Prevent infinite recursion
            return null;
        }
        
        long currentHead = localHead.get();
        
        if (currentHead == localTail.get()) {
            return null;
        }
        
        Order order = readOrderFromBuffer(currentHead);
        
        // Validate order before returning
        if (order != null && !order.isValid()) {
            logger.warn("Invalid order read from buffer at position {}: {}", currentHead, order);
            // Skip this invalid order and move to next
            long nextHead = (currentHead + 1) % SharedMemoryLayout.ORDER_QUEUE_CAPACITY;
            localHead.set(nextHead);
            memoryManager.setOrderQueueHead(nextHead);
            return pollWithSkipCount(skipCount + 1); // Recursive call with skip counter
        }
        
        long nextHead = (currentHead + 1) % SharedMemoryLayout.ORDER_QUEUE_CAPACITY;
        localHead.set(nextHead);
        memoryManager.setOrderQueueHead(nextHead);
        
        return order;
    }
    
    public Order peek() {
        if (!initialized) {
            return null;
        }
        
        long currentHead = localHead.get();
        
        if (currentHead == localTail.get()) {
            return null;
        }
        
        return readOrderFromBuffer(currentHead);
    }
    
    public boolean isEmpty() {
        return localHead.get() == localTail.get();
    }
    
    public boolean isFull() {
        long nextTail = (localTail.get() + 1) % SharedMemoryLayout.ORDER_QUEUE_CAPACITY;
        return nextTail == localHead.get();
    }
    
    public long size() {
        long head = localHead.get();
        long tail = localTail.get();
        
        if (tail >= head) {
            return tail - head;
        } else {
            return (SharedMemoryLayout.ORDER_QUEUE_CAPACITY - head) + tail;
        }
    }
    
    public long capacity() {
        return SharedMemoryLayout.ORDER_QUEUE_CAPACITY - 1; // -1 because we need one empty slot to distinguish between full and empty
    }
    
    public double utilizationPercentage() {
        return (double) size() / capacity() * 100.0;
    }
    
    private void writeOrderToBuffer(Order order, long index) {
        int offset = SharedMemoryLayout.getOrderOffset((int) index);
        buffer.position(offset);
        order.writeToBuffer(buffer);
    }
    
    private Order readOrderFromBuffer(long index) {
        int offset = SharedMemoryLayout.getOrderOffset((int) index);
        buffer.position(offset);
        
        Order order = new Order();
        order.readFromBuffer(buffer);
        return order;
    }
    
    public void syncWithMemory() {
        if (initialized) {
            localHead.set(memoryManager.getOrderQueueHead());
            localTail.set(memoryManager.getOrderQueueTail());
        }
    }
    
    public void clear() {
        localHead.set(0);
        localTail.set(0);
        memoryManager.setOrderQueueHead(0);
        memoryManager.setOrderQueueTail(0);
        logger.info("OrderQueue cleared");
    }
    
    public long getHead() {
        return localHead.get();
    }
    
    public long getTail() {
        return localTail.get();
    }
    
    @Override
    public String toString() {
        return String.format("OrderQueue{head=%d, tail=%d, size=%d, capacity=%d, utilization=%.2f%%}",
                getHead(), getTail(), size(), capacity(), utilizationPercentage());
    }
}