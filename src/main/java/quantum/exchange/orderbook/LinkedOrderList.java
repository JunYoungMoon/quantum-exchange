package quantum.exchange.orderbook;

import lombok.extern.slf4j.Slf4j;
import quantum.exchange.model.Order;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 이중 연결 리스트로 구현된 주문 목록
 * 효율적인 삽입, 삭제, 순회를 지원한다.
 */
@Slf4j
public class LinkedOrderList implements Iterable<Order> {
    
    private OrderNode head;
    private OrderNode tail;
    private int size;
    private long totalQuantity;
    
    public LinkedOrderList() {
        this.head = null;
        this.tail = null;
        this.size = 0;
        this.totalQuantity = 0;
    }
    
    public void addOrder(Order order) {
        OrderNode newNode = new OrderNode(order);
        
        if (head == null) {
            head = tail = newNode;
        } else {
            tail.next = newNode;
            newNode.prev = tail;
            tail = newNode;
        }
        
        size++;
        totalQuantity += order.getQuantity();
    }
    
    public Order removeFirst() {
        if (head == null) {
            return null;
        }
        
        Order order = head.order;
        totalQuantity -= order.getQuantity();
        
        if (head == tail) {
            head = tail = null;
        } else {
            head = head.next;
            head.prev = null;
        }
        
        size--;
        return order;
    }
    
    public boolean removeOrder(long orderId) {
        OrderNode current = head;
        
        while (current != null) {
            if (current.order.getOrderId() == orderId) {
                totalQuantity -= current.order.getQuantity();
                
                if (current.prev != null) {
                    current.prev.next = current.next;
                } else {
                    head = current.next;
                }
                
                if (current.next != null) {
                    current.next.prev = current.prev;
                } else {
                    tail = current.prev;
                }
                
                size--;
                return true;
            }
            current = current.next;
        }
        
        return false;
    }
    
    public boolean updateOrderQuantity(long orderId, long newQuantity) {
        OrderNode current = head;
        
        while (current != null) {
            if (current.order.getOrderId() == orderId) {
                long oldQuantity = current.order.getQuantity();
                current.order.setQuantity(newQuantity);
                totalQuantity += (newQuantity - oldQuantity);
                
                if (newQuantity <= 0) {
                    return removeOrder(orderId);
                }
                
                return true;
            }
            current = current.next;
        }
        
        return false;
    }
    
    public Order peekFirst() {
        return head != null ? head.order : null;
    }
    
    public boolean isEmpty() {
        return size == 0;
    }
    
    public int size() {
        return size;
    }
    
    public long getTotalQuantity() {
        return totalQuantity;
    }
    
    public List<Order> getAllOrders() {
        List<Order> orders = new ArrayList<>();
        OrderNode current = head;
        
        while (current != null) {
            orders.add(current.order);
            current = current.next;
        }
        
        return orders;
    }
    
    public void clear() {
        head = tail = null;
        size = 0;
        totalQuantity = 0;
    }
    
    @Override
    public Iterator<Order> iterator() {
        return new LinkedOrderIterator();
    }
    
    private static class OrderNode {
        Order order;
        OrderNode next;
        OrderNode prev;
        
        OrderNode(Order order) {
            this.order = order;
            this.next = null;
            this.prev = null;
        }
    }
    
    private class LinkedOrderIterator implements Iterator<Order> {
        private OrderNode current = head;
        private OrderNode lastReturned;
        
        @Override
        public boolean hasNext() {
            return current != null;
        }
        
        @Override
        public Order next() {
            if (current == null) {
                throw new java.util.NoSuchElementException();
            }
            
            lastReturned = current;
            current = current.next;
            return lastReturned.order;
        }
        
        @Override
        public void remove() {
            if (lastReturned == null) {
                throw new IllegalStateException();
            }
            
            LinkedOrderList.this.removeOrder(lastReturned.order.getOrderId());
            lastReturned = null;
        }
    }
    
    @Override
    public String toString() {
        return String.format("LinkedOrderList{size=%d, totalQty=%d}", size, totalQuantity);
    }
}