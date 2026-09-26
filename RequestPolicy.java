package domain;

public class RequestPolicy {

    public RequestStatus move(RequestId id, RequestStatus from, RequestStatus to) {
        if (id == null) {
            throw new IllegalArgumentException("ID не может быть null");
        }

        if (from == RequestStatus.DRAFT && to == RequestStatus.APPROVED) {
            return to;
        }
        if (from == RequestStatus.APPROVED && to == RequestStatus.ORDERED) {
            return to;
        }

        throw new IllegalStateException("Запрещенный переход из " + from + " в " + to);
    }
}