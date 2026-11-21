package org.narxoz.client;

import org.junit.jupiter.api.Test;
import org.narxoz.config.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The dev profile binds app.downstream from yaml. No socket is opened to port 9090:
 * this only proves the values come from configuration, not from a Java string.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class DownstreamPropertiesTest {

    @Autowired
    private DownstreamProperties downstream;

    @Autowired
    private LoggingRequestNotifier notifier;

    @Test
    void devProfileBindsBaseUrlAndTimeout() {
        assertEquals(URI.create("http://localhost:9090"), downstream.baseUrl());
        assertEquals(Duration.ofSeconds(2), downstream.timeout());
    }

    @Test
    void outboundAdapterReceivesTheBoundValues() {
        assertSame(downstream, notifier.downstream());
    }
}
