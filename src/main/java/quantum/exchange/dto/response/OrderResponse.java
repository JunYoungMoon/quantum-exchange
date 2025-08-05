package quantum.exchange.dto.response;

public record OrderResponse(Long orderId, String status) {

    @Override
    public String toString() {
        return String.format("OrderResponse{orderId=%d, status='%s'}", orderId, status);
    }
}