package quantum.exchange.example;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SimpleChronicleTest implements CommandLineRunner {
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n=== Chronicle Queue Simple Test ===");
        
        // Test Chronicle Queue
        testChronicleQueue();
        
        System.out.println("\n=== Test Complete ===\n");
    }
    
    private void testChronicleQueue() {
        String queuePath = "./data/test-queue";
        
        // Write messages
        try (ChronicleQueue queue = ChronicleQueue.single(queuePath)) {
            ExcerptAppender appender = queue.acquireAppender();
            
            // Write simple messages
            appender.writeDocument(wire -> wire.write("message").text("Hello Chronicle Queue"));
            appender.writeDocument(wire -> wire.write("message").text("Second message"));
            
            // Write trade data
            appender.writeDocument(wire -> wire
                .write("symbol").text("BTC")
                .write("price").float64(50000.0)
                .write("quantity").int64(100)
                .write("timestamp").int64(System.currentTimeMillis()));
                
            System.out.println("✅ Written 3 messages to queue");
        }
        
        // Read messages
        try (ChronicleQueue queue = ChronicleQueue.single(queuePath)) {
            ExcerptTailer tailer = queue.createTailer();
            
            System.out.println("📖 Reading messages:");
            
            // Read all messages
            while (tailer.readDocument(wire -> {
                if (wire != null) {
                    if (wire.hasMore()) {
                        String key = wire.readEvent(String.class);
                        if ("message".equals(key)) {
                            String message = wire.getValueIn().text();
                            System.out.println("  - Message: " + message);
                        } else if ("symbol".equals(key)) {
                            String symbol = wire.getValueIn().text();
                            double price = wire.read("price").float64();
                            long quantity = wire.read("quantity").int64();
                            long timestamp = wire.read("timestamp").int64();
                            System.out.println("  - Trade: " + symbol + " $" + price + " qty:" + quantity + " @" + timestamp);
                        }
                    }
                }
            })) {
                // Continue reading
            }
        }
    }
}