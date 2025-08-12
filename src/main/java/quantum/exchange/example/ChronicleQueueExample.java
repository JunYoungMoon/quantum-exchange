package quantum.exchange.example;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;

@Component
public class ChronicleQueueExample {
    
    private final String queuePath = "./data/queue";
    
    public void writeMessage(String message) {
        try (ChronicleQueue queue = ChronicleQueue.single(queuePath)) {
            ExcerptAppender appender = queue.acquireAppender();
            appender.writeDocument(wire -> wire.write("message").text(message));
            System.out.println("Written: " + message);
        }
    }
    
    public String readMessage() {
        try (ChronicleQueue queue = ChronicleQueue.single(queuePath)) {
            ExcerptTailer tailer = queue.createTailer();
            StringBuilder result = new StringBuilder();
            
            tailer.readDocument(wire -> {
                if (wire != null) {
                    String message = wire.read("message").text();
                    result.append(message);
                    System.out.println("Read: " + message);
                }
            });
            
            return result.toString();
        }
    }
    
    public void writeTradeData(String symbol, double price, long quantity) {
        try (ChronicleQueue queue = ChronicleQueue.single(queuePath)) {
            ExcerptAppender appender = queue.acquireAppender();
            appender.writeDocument(wire -> wire
                .write("symbol").text(symbol)
                .write("price").float64(price)
                .write("quantity").int64(quantity)
                .write("timestamp").int64(System.currentTimeMillis()));
        }
    }
}