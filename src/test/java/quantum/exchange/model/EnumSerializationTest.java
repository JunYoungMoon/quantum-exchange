package quantum.exchange.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EnumSerializationTest {
    
    @Test
    void testOrderTypeSerializationStartsFromZero() {
        // Test that enum values start from 0 to handle default memory initialization
        assertEquals(0, OrderType.LIMIT.getValue());
        assertEquals(1, OrderType.MARKET.getValue());
        assertEquals(2, OrderType.MARKET_WITH_PRICE.getValue());
        
        // Test deserialization from valid values
        assertEquals(OrderType.LIMIT, OrderType.fromValue(0));
        assertEquals(OrderType.MARKET, OrderType.fromValue(1));
        assertEquals(OrderType.MARKET_WITH_PRICE, OrderType.fromValue(2));
        
        // Test invalid value handling (should not throw exception)
        assertEquals(OrderType.LIMIT, OrderType.fromValue(-1)); // Default fallback
        assertEquals(OrderType.LIMIT, OrderType.fromValue(999)); // Default fallback
    }
    
    @Test
    void testOrderSideSerializationStartsFromZero() {
        // Test that enum values start from 0 to handle default memory initialization
        assertEquals(0, OrderSide.BUY.getValue());
        assertEquals(1, OrderSide.SELL.getValue());
        
        // Test deserialization from valid values
        assertEquals(OrderSide.BUY, OrderSide.fromValue(0));
        assertEquals(OrderSide.SELL, OrderSide.fromValue(1));
        
        // Test invalid value handling (should not throw exception)
        assertEquals(OrderSide.BUY, OrderSide.fromValue(-1)); // Default fallback
        assertEquals(OrderSide.BUY, OrderSide.fromValue(999)); // Default fallback
    }
    
    @Test
    void testOrderValidation() {
        // Test valid limit order
        Order validLimitOrder = new Order(1, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 50000, 10, System.nanoTime());
        assertTrue(validLimitOrder.isValid());
        
        // Test valid market order
        Order validMarketOrder = new Order(2, "BTC-USD", OrderSide.SELL, OrderType.MARKET, 0, 5, System.nanoTime());
        assertTrue(validMarketOrder.isValid());
        
        // Test invalid orders
        Order invalidOrder1 = new Order(0, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 50000, 10, System.nanoTime()); // Invalid ID
        assertFalse(invalidOrder1.isValid());
        
        Order invalidOrder2 = new Order(1, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 0, 10, System.nanoTime()); // Invalid price for limit order
        assertFalse(invalidOrder2.isValid());
        
        Order invalidOrder3 = new Order(1, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 50000, 0, System.nanoTime()); // Invalid quantity
        assertFalse(invalidOrder3.isValid());
        
        Order invalidOrder4 = new Order(1, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, 50000, 10, 0); // Invalid timestamp
        assertFalse(invalidOrder4.isValid());
    }
}