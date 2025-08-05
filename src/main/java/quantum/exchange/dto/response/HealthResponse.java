package quantum.exchange.dto.response;

public record HealthResponse(String status, boolean engineRunning) {

    @Override
    public String toString() {
        return String.format("HealthResponse{status='%s', engineRunning=%s}", status, engineRunning);
    }
}