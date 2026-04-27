package com.wpanther.cancellationnote.processing.application.port.out;

import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedDomainEvent;

public interface CancellationNoteEventPublishingPort {

    void publish(CancellationNoteProcessedDomainEvent event);
}