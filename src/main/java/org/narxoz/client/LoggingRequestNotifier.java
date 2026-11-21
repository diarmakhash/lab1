package org.narxoz.client;

import org.narxoz.domain.RequestId;
import org.narxoz.domain.RequestNotifier;
import org.narxoz.domain.RequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter for the outbound port. It already knows the downstream base URL and timeout
 * from yaml, but does not open a socket yet: the real HTTP call is Lab 6.
 */
@Component
public class LoggingRequestNotifier implements RequestNotifier {
    private static final Logger log = LoggerFactory.getLogger(LoggingRequestNotifier.class);

    private final DownstreamProperties downstream;

    public LoggingRequestNotifier(DownstreamProperties downstream) {
        this.downstream = downstream;
    }

    @Override
    public void statusChanged(RequestId id, RequestStatus to) {
        log.info("purchase request {} moved to {} (would notify {} within {})",
                id.value(), to, downstream.baseUrl(), downstream.timeout());
    }

    DownstreamProperties downstream() {
        return downstream;
    }
}
