package org.narxoz.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequestPolicyTest {

    private final RequestPolicy policy = new RequestPolicy(List.of(
        new TransitionRule(),
        new OnlyApprovedCanCancelRule()
    ));
    private final RequestId validId = new RequestId("REQ-001");

    @ParameterizedTest
    @CsvSource({
        "DRAFT, APPROVED, true",
        "APPROVED, ORDERED, true",
        "APPROVED, CANCELLED, true",
        "DRAFT, ORDERED, false",
        "DRAFT, CANCELLED, false",
        "REJECTED, CANCELLED, false"
    })
    
    void testTransitions(String fromStr, String toStr, boolean isAllowed) {
        RequestStatus from = RequestStatus.valueOf(fromStr);
        RequestStatus to = RequestStatus.valueOf(toStr);

        if (isAllowed) {
            assertEquals(to, policy.move(validId, from, to));
        } else {
            assertThrows(IllegalStateException.class, () -> policy.move(validId, from, to));
        }
    }

    @Test
    void testNullIdThrowsException() {
        assertThrows(NullPointerException.class, () ->
            policy.move(null, RequestStatus.DRAFT, RequestStatus.APPROVED)
        );
    }
}