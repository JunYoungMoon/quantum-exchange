package quantum.exchange.queue;

import quantum.exchange.model.Order;

/**
 * 주문 큐의 공통 인터페이스
 * 메모리 맵 큐와 Chronicle Queue 구현체가 모두 이 인터페이스를 구현한다.
 */
public interface OrderQueueInterface {
    
    /**
     * 주문을 큐에 추가한다.
     */
    boolean offer(Order order);
    
    /**
     * 큐에서 주문을 꺼낸다.
     */
    Order poll();
    
    /**
     * 큐의 첫 번째 주문을 확인한다.
     */
    Order peek();
    
    /**
     * 큐가 비어있는지 확인한다.
     */
    boolean isEmpty();
    
    /**
     * 큐가 가득 찼는지 확인한다.
     */
    boolean isFull();
    
    /**
     * 큐의 현재 크기를 반환한다.
     */
    long size();
    
    /**
     * 큐의 용량을 반환한다.
     */
    long capacity();
    
    /**
     * 큐 사용률을 백분율로 반환한다.
     */
    double utilizationPercentage();
    
    /**
     * 메모리와 동기화한다.
     */
    void syncWithMemory();
    
    /**
     * 큐를 비운다.
     */
    void clear();
    
    /**
     * Head 위치를 반환한다.
     */
    long getHead();
    
    /**
     * Tail 위치를 반환한다.
     */
    long getTail();
}