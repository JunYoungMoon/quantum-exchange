package quantum.exchange.dto.response;

public record ErrorResponse(String error) {

    @Override
    public String toString() {
        return String.format("ErrorResponse{error='%s'}", error);
    }
}