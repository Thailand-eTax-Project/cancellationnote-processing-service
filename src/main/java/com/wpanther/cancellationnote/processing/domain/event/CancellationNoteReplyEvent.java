package com.wpanther.cancellationnote.processing.domain.event;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.model.SagaReply;

public class CancellationNoteReplyEvent extends SagaReply {

    private static final long serialVersionUID = 1L;

    public static CancellationNoteReplyEvent success(String sagaId, String sagaStep, String correlationId) {
        return new CancellationNoteReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS);
    }

    public static CancellationNoteReplyEvent failure(String sagaId, String sagaStep, String correlationId,
                                                 String errorMessage) {
        return new CancellationNoteReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
    }

    public static CancellationNoteReplyEvent compensated(String sagaId, String sagaStep, String correlationId) {
        return new CancellationNoteReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
    }

    private CancellationNoteReplyEvent(String sagaId, String sagaStep, String correlationId, ReplyStatus status) {
        super(sagaId, sagaStep, correlationId, status);
    }

    private CancellationNoteReplyEvent(String sagaId, String sagaStep, String correlationId, String errorMessage) {
        super(sagaId, sagaStep, correlationId, errorMessage);
    }
}
