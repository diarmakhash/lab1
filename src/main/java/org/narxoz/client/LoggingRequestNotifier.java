package org.narxoz.client;

import org.narxoz.domain.RequestId;
import org.narxoz.domain.RequestNotifier;
import org.narxoz.domain.RequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Placeholder adapter for the outbound port. A real HTTP client comes in a later lab. */
@Component
public class LoggingRequestNotifier implements RequestNotifier {
    private static final Logger log = LoggerFactory.getLogger(LoggingRequestNotifier.class);

    @Override
    public void statusChanged(RequestId id, RequestStatus to) {
        log.info("purchase request {} moved to {}", id.value(), to);
    }
}
