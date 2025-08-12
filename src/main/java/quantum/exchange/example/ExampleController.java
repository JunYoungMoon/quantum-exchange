package quantum.exchange.example;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/examples")
@RequiredArgsConstructor
public class ExampleController {
    
    private final ChronicleQueueExample queueExample;
    private final ChronicleMapExample mapExample;
    
    @PostConstruct
    public void init() {
        // mapExample.initializeMaps();
    }
    
    @PostMapping("/queue/write")
    public String writeToQueue(@RequestParam String message) {
        queueExample.writeMessage(message);
        return "Message written to queue";
    }
    
    @GetMapping("/queue/read")
    public String readFromQueue() {
        return queueExample.readMessage();
    }
    
    @PostMapping("/queue/trade")
    public String writeTradeData(@RequestParam String symbol, 
                               @RequestParam double price, 
                               @RequestParam long quantity) {
        queueExample.writeTradeData(symbol, price, quantity);
        return "Trade data written to queue";
    }
    
    @PostMapping("/map/string")
    public String putString(@RequestParam String key, @RequestParam String value) {
        mapExample.putString(key, value);
        return "String stored in map";
    }
    
    @GetMapping("/map/string/{key}")
    public String getString(@PathVariable String key) {
        return mapExample.getString(key);
    }
    
    @PostMapping("/map/price")
    public String putPrice(@RequestParam String symbol, @RequestParam double price) {
        mapExample.putPrice(symbol, price);
        return "Price stored in map";
    }
    
    @GetMapping("/map/price/{symbol}")
    public Double getPrice(@PathVariable String symbol) {
        return mapExample.getPrice(symbol);
    }
    
    @PutMapping("/map/price")
    public String updatePrice(@RequestParam String symbol, @RequestParam double price) {
        mapExample.updatePrice(symbol, price);
        return "Price updated in map";
    }
}