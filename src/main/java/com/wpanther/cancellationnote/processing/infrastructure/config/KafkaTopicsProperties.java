package com.wpanther.cancellationnote.processing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topics")
public record KafkaTopicsProperties(
        String cancellationNoteProcessed,
        String dlq,
        String sagaCommandCancellationNote,
        String sagaCompensationCancellationNote,
        String sagaReplyCancellationNote) {
}
