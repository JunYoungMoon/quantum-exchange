package quantum.exchange.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public class MmapOrderBookManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MmapOrderBookManager.class);
    
    private final String filePath;
    private RandomAccessFile file;
    private FileChannel channel;
    private MappedByteBuffer buffer;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    
    public MmapOrderBookManager(String filePath) {
        this.filePath = filePath;
    }
    
    public void initialize() throws Exception {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                createDirectoriesIfNeeded();
                file = new RandomAccessFile(filePath, "rw");
                channel = file.getChannel();
                
                if (file.length() < SharedMemoryLayout.TOTAL_SIZE) {
                    file.setLength(SharedMemoryLayout.TOTAL_SIZE);
                    logger.info("Extended file to {} bytes", SharedMemoryLayout.TOTAL_SIZE);
                }
                
                buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, SharedMemoryLayout.TOTAL_SIZE);
                
                initializeHeader();
                
                logger.info("Memory-mapped file initialized: {}", filePath);
                logger.info(SharedMemoryLayout.getLayoutInfo());
                
            } catch (Exception e) {
                isInitialized.set(false);
                cleanup();
                throw new RuntimeException("Failed to initialize memory-mapped file", e);
            }
        }
    }
    
    private void createDirectoriesIfNeeded() {
        Path path = Paths.get(filePath);
        File parentDir = path.getParent().toFile();
        if (!parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (!created) {
                logger.warn("Could not create directories for path: {}", parentDir);
            }
        }
    }
    
    private void initializeHeader() {
        if (!isHeaderInitialized()) {
            buffer.putLong(SharedMemoryLayout.Header.ORDER_QUEUE_HEAD_OFFSET, 0);
            buffer.putLong(SharedMemoryLayout.Header.ORDER_QUEUE_TAIL_OFFSET, 0);
            buffer.putLong(SharedMemoryLayout.Header.TRADE_QUEUE_HEAD_OFFSET, 0);
            buffer.putLong(SharedMemoryLayout.Header.TRADE_QUEUE_TAIL_OFFSET, 0);
            buffer.putLong(SharedMemoryLayout.Header.NEXT_TRADE_ID_OFFSET, 1);
            buffer.putLong(SharedMemoryLayout.Header.TIMESTAMP_OFFSET, System.nanoTime());
            buffer.putLong(SharedMemoryLayout.Header.VERSION_OFFSET, 1);
            buffer.putLong(SharedMemoryLayout.Header.STATUS_OFFSET, 1); // 1 = active
            
            buffer.force();
            logger.info("Header initialized");
        } else {
            logger.info("Header already initialized, skipping");
        }
    }
    
    private boolean isHeaderInitialized() {
        return buffer.getLong(SharedMemoryLayout.Header.VERSION_OFFSET) > 0;
    }
    
    public MappedByteBuffer getBuffer() {
        ensureInitialized();
        return buffer;
    }
    
    public long getOrderQueueHead() {
        ensureInitialized();
        return buffer.getLong(SharedMemoryLayout.Header.ORDER_QUEUE_HEAD_OFFSET);
    }
    
    public long getOrderQueueTail() {
        ensureInitialized();
        return buffer.getLong(SharedMemoryLayout.Header.ORDER_QUEUE_TAIL_OFFSET);
    }
    
    public void setOrderQueueHead(long head) {
        ensureInitialized();
        buffer.putLong(SharedMemoryLayout.Header.ORDER_QUEUE_HEAD_OFFSET, head);
    }
    
    public void setOrderQueueTail(long tail) {
        ensureInitialized();
        buffer.putLong(SharedMemoryLayout.Header.ORDER_QUEUE_TAIL_OFFSET, tail);
    }
    
    public long getTradeQueueHead() {
        ensureInitialized();
        return buffer.getLong(SharedMemoryLayout.Header.TRADE_QUEUE_HEAD_OFFSET);
    }
    
    public long getTradeQueueTail() {
        ensureInitialized();
        return buffer.getLong(SharedMemoryLayout.Header.TRADE_QUEUE_TAIL_OFFSET);
    }
    
    public void setTradeQueueHead(long head) {
        ensureInitialized();
        buffer.putLong(SharedMemoryLayout.Header.TRADE_QUEUE_HEAD_OFFSET, head);
    }
    
    public void setTradeQueueTail(long tail) {
        ensureInitialized();
        buffer.putLong(SharedMemoryLayout.Header.TRADE_QUEUE_TAIL_OFFSET, tail);
    }
    
    public long getNextTradeId() {
        ensureInitialized();
        return buffer.getLong(SharedMemoryLayout.Header.NEXT_TRADE_ID_OFFSET);
    }
    
    public long incrementAndGetTradeId() {
        ensureInitialized();
        int offset = SharedMemoryLayout.Header.NEXT_TRADE_ID_OFFSET;
        long current = buffer.getLong(offset);
        long next = current + 1;
        buffer.putLong(offset, next);
        return next;
    }
    
    public void updateTimestamp() {
        ensureInitialized();
        buffer.putLong(SharedMemoryLayout.Header.TIMESTAMP_OFFSET, System.nanoTime());
    }
    
    public long getTimestamp() {
        ensureInitialized();
        return buffer.getLong(SharedMemoryLayout.Header.TIMESTAMP_OFFSET);
    }
    
    public boolean isActive() {
        ensureInitialized();
        return buffer.getLong(SharedMemoryLayout.Header.STATUS_OFFSET) == 1;
    }
    
    public void setActive(boolean active) {
        ensureInitialized();
        buffer.putLong(SharedMemoryLayout.Header.STATUS_OFFSET, active ? 1 : 0);
        buffer.force();
    }
    
    public void force() {
        if (buffer != null) {
            buffer.force();
        }
    }
    
    private void ensureInitialized() {
        if (!isInitialized.get() || isClosed.get()) {
            throw new IllegalStateException("MmapOrderBookManager is not initialized or has been closed");
        }
    }
    
    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            cleanup();
            logger.info("MmapOrderBookManager closed");
        }
    }
    
    private void cleanup() {
        try {
            if (buffer != null) {
                buffer.force();
                buffer = null;
            }
            if (channel != null) {
                channel.close();
                channel = null;
            }
            if (file != null) {
                file.close();
                file = null;
            }
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        }
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public boolean isInitialized() {
        return isInitialized.get();
    }
    
    public boolean isClosed() {
        return isClosed.get();
    }
}