package quantum.exchange.dto.response;


public record SuccessResponse(String message) {

    @Override
    public String toString() {
        return String.format("SuccessResponse{message='%s'}", message);
    }
}