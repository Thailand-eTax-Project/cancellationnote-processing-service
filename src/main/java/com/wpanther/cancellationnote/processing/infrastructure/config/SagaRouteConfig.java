package com.wpanther.cancellationnote.processing.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;

/**
 * @deprecated Superseded by {@code infrastructure.adapter.in.messaging.SagaRouteConfig}.
 * TODO: Delete in Task 11 (final cleanup).
 */
@Deprecated
@Slf4j
public class SagaRouteConfig extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // Stub - superseded by infrastructure.adapter.in.messaging.SagaRouteConfig
    }
}
