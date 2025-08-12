package quantum.exchange.example;

import net.openhft.chronicle.map.ChronicleMap;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class ChronicleMapExample {
    
    private ChronicleMap<String, String> stringMap;
    private ChronicleMap<String, Double> priceMap;
    
    public void initializeMaps() throws IOException {
        // Simple string key-value map
        stringMap = ChronicleMap
            .of(String.class, String.class)
            .name("string-map")
            .entries(10000)
            .averageKeySize(20)
            .averageValueSize(50)
            .createPersistedTo(new File("./data/string-map.dat"));
        
        // Price map for trading symbols
        priceMap = ChronicleMap
            .of(String.class, Double.class)
            .name("price-map")
            .entries(1000)
            .averageKeySize(10)
            .createPersistedTo(new File("./data/price-map.dat"));
    }
    
    public void putString(String key, String value) {
        stringMap.put(key, value);
        System.out.println("Stored: " + key + " = " + value);
    }
    
    public String getString(String key) {
        String value = stringMap.get(key);
        System.out.println("Retrieved: " + key + " = " + value);
        return value;
    }
    
    public void putPrice(String symbol, Double price) {
        priceMap.put(symbol, price);
        System.out.println("Price stored: " + symbol + " = $" + price);
    }
    
    public Double getPrice(String symbol) {
        Double price = priceMap.get(symbol);
        System.out.println("Price retrieved: " + symbol + " = $" + price);
        return price;
    }
    
    public void updatePrice(String symbol, Double newPrice) {
        Double oldPrice = priceMap.replace(symbol, newPrice);
        System.out.println("Price updated: " + symbol + " from $" + oldPrice + " to $" + newPrice);
    }
    
    public boolean symbolExists(String symbol) {
        return priceMap.containsKey(symbol);
    }
    
    public void closeMaps() {
        if (stringMap != null) stringMap.close();
        if (priceMap != null) priceMap.close();
    }
}