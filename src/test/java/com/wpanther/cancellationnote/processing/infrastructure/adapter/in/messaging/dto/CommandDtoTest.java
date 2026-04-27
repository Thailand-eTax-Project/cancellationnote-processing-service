package com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommandDtoTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // --- ProcessCancellationNoteCommand tests ---

    @Test
    @DisplayName("ProcessCancellationNoteCommand: simple constructor sets all fields")
    void processCommand_simpleConstructor_setsAllFields() {
        ProcessCancellationNoteCommand cmd = new ProcessCancellationNoteCommand(
                "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1",
                "doc-1", "<xml/>", "CN001");

        assertThat(cmd.getSagaId()).isEqualTo("saga-1");
        assertThat(cmd.getSagaStep()).isEqualTo(SagaStep.PROCESS_CANCELLATION_NOTE);
        assertThat(cmd.getCorrelationId()).isEqualTo("corr-1");
        assertThat(cmd.getDocumentId()).isEqualTo("doc-1");
        assertThat(cmd.getXmlContent()).isEqualTo("<xml/>");
        assertThat(cmd.getCancellationNoteNumber()).isEqualTo("CN001");
    }

    @Test
    @DisplayName("ProcessCancellationNoteCommand: JsonCreator constructor sets all fields")
    void processCommand_jsonCreatorConstructor_setsAllFields() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        ProcessCancellationNoteCommand cmd = new ProcessCancellationNoteCommand(
                eventId, now, "saga.command.cancellation-note", 1,
                "saga-2", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-2",
                "doc-2", "<xml/>", "CN002");

        assertThat(cmd.getEventId()).isEqualTo(eventId);
        assertThat(cmd.getOccurredAt()).isEqualTo(now);
        assertThat(cmd.getEventType()).isEqualTo("saga.command.cancellation-note");
        assertThat(cmd.getVersion()).isEqualTo(1);
        assertThat(cmd.getSagaId()).isEqualTo("saga-2");
        assertThat(cmd.getSagaStep()).isEqualTo(SagaStep.PROCESS_CANCELLATION_NOTE);
        assertThat(cmd.getCorrelationId()).isEqualTo("corr-2");
        assertThat(cmd.getDocumentId()).isEqualTo("doc-2");
        assertThat(cmd.getXmlContent()).isEqualTo("<xml/>");
        assertThat(cmd.getCancellationNoteNumber()).isEqualTo("CN002");
    }

    @Test
    @DisplayName("ProcessCancellationNoteCommand: JSON serialization and deserialization round-trip")
    void processCommand_jsonRoundTrip() throws JsonProcessingException {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        ProcessCancellationNoteCommand original = new ProcessCancellationNoteCommand(
                eventId, now, "saga.command.cancellation-note", 1,
                "saga-3", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-3",
                "doc-3", "<xml>content</xml>", "CN003");

        String json = objectMapper.writeValueAsString(original);
        ProcessCancellationNoteCommand deserialized = objectMapper.readValue(json,
                ProcessCancellationNoteCommand.class);

        assertThat(deserialized.getEventId()).isEqualTo(eventId);
        assertThat(deserialized.getSagaId()).isEqualTo("saga-3");
        assertThat(deserialized.getCorrelationId()).isEqualTo("corr-3");
        assertThat(deserialized.getDocumentId()).isEqualTo("doc-3");
        assertThat(deserialized.getXmlContent()).isEqualTo("<xml>content</xml>");
        assertThat(deserialized.getCancellationNoteNumber()).isEqualTo("CN003");
        assertThat(deserialized.getSagaStep()).isEqualTo(SagaStep.PROCESS_CANCELLATION_NOTE);
    }

    // --- CompensateCancellationNoteCommand tests ---

    @Test
    @DisplayName("CompensateCancellationNoteCommand: simple constructor sets all fields")
    void compensateCommand_simpleConstructor_setsAllFields() {
        CompensateCancellationNoteCommand cmd = new CompensateCancellationNoteCommand(
                "saga-10", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-10",
                "process-cancellation-note", "doc-10", "CancellationNote");

        assertThat(cmd.getSagaId()).isEqualTo("saga-10");
        assertThat(cmd.getSagaStep()).isEqualTo(SagaStep.PROCESS_CANCELLATION_NOTE);
        assertThat(cmd.getCorrelationId()).isEqualTo("corr-10");
        assertThat(cmd.getStepToCompensate()).isEqualTo("process-cancellation-note");
        assertThat(cmd.getDocumentId()).isEqualTo("doc-10");
        assertThat(cmd.getDocumentType()).isEqualTo("CancellationNote");
    }

    @Test
    @DisplayName("CompensateCancellationNoteCommand: JsonCreator constructor sets all fields")
    void compensateCommand_jsonCreatorConstructor_setsAllFields() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        CompensateCancellationNoteCommand cmd = new CompensateCancellationNoteCommand(
                eventId, now, "saga.compensation.cancellation-note", 1,
                "saga-11", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-11",
                "process-cancellation-note", "doc-11", "CancellationNote");

        assertThat(cmd.getEventId()).isEqualTo(eventId);
        assertThat(cmd.getOccurredAt()).isEqualTo(now);
        assertThat(cmd.getEventType()).isEqualTo("saga.compensation.cancellation-note");
        assertThat(cmd.getVersion()).isEqualTo(1);
        assertThat(cmd.getSagaId()).isEqualTo("saga-11");
        assertThat(cmd.getSagaStep()).isEqualTo(SagaStep.PROCESS_CANCELLATION_NOTE);
        assertThat(cmd.getCorrelationId()).isEqualTo("corr-11");
        assertThat(cmd.getStepToCompensate()).isEqualTo("process-cancellation-note");
        assertThat(cmd.getDocumentId()).isEqualTo("doc-11");
        assertThat(cmd.getDocumentType()).isEqualTo("CancellationNote");
    }

    @Test
    @DisplayName("CompensateCancellationNoteCommand: JSON serialization and deserialization round-trip")
    void compensateCommand_jsonRoundTrip() throws JsonProcessingException {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        CompensateCancellationNoteCommand original = new CompensateCancellationNoteCommand(
                eventId, now, "saga.compensation.cancellation-note", 1,
                "saga-12", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-12",
                "process-cancellation-note", "doc-12", "CancellationNote");

        String json = objectMapper.writeValueAsString(original);
        CompensateCancellationNoteCommand deserialized = objectMapper.readValue(json,
                CompensateCancellationNoteCommand.class);

        assertThat(deserialized.getEventId()).isEqualTo(eventId);
        assertThat(deserialized.getSagaId()).isEqualTo("saga-12");
        assertThat(deserialized.getCorrelationId()).isEqualTo("corr-12");
        assertThat(deserialized.getStepToCompensate()).isEqualTo("process-cancellation-note");
        assertThat(deserialized.getDocumentId()).isEqualTo("doc-12");
        assertThat(deserialized.getDocumentType()).isEqualTo("CancellationNote");
        assertThat(deserialized.getSagaStep()).isEqualTo(SagaStep.PROCESS_CANCELLATION_NOTE);
    }
}
