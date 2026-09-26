package domain;

public record RequestId(String value) {
    public RequestId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ID не может быть пустым");
        }
    }
}