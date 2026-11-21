package org.narxoz.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Where the outbound port will call, and how long it may wait.
 * Bound from yaml (app.downstream.*): no URL string lives in Java.
 * Lab 6 makes the real call and enforces the timeout; this lab only binds the values.
 */
@ConfigurationProperties(prefix = "app.downstream")
public record DownstreamProperties(URI baseUrl, Duration timeout) {
}
