package org.narxoz.config;

import org.junit.jupiter.api.Test;
import org.narxoz.domain.RequestId;
import org.narxoz.domain.RequestNotifier;
import org.narxoz.domain.RequestStatus;
import org.narxoz.persistence.RequestJdbc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Week 4: the outbound port is a Mockito mock. No real HTTP, no database. */
class RequestServiceTest {

    private final RequestNotifier notifier = mock(RequestNotifier.class);
    private final RequestService service = new RequestService(
            new AppConfig().rules(),
            mock(RequestJdbc.class),
            notifier);

    @Test
    void allowedMoveNotifiesThePort() {
        RequestId id = RequestId.newId();

        assertEquals(RequestStatus.APPROVED,
                service.processMove(id, RequestStatus.DRAFT, RequestStatus.APPROVED));

        verify(notifier).statusChanged(id, RequestStatus.APPROVED);
    }

    @Test
    void forbiddenMoveDoesNotNotify() {
        RequestId id = RequestId.newId();

        assertThrows(IllegalStateException.class,
                () -> service.processMove(id, RequestStatus.DRAFT, RequestStatus.ORDERED));

        verifyNoInteractions(notifier);
    }
}
