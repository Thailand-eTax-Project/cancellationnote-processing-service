package com.wpanther.cancellationnote.processing.application.dto.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationNoteProcessedEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("simple constructor sets all fields correctly")
    void simpleConstructor_setsAllFieldsCorrectly() {
        CancellationNoteProcessedEvent event = new CancellationNoteProcessedEvent(
                "note-123", "CN001", new BigDecimal("1000.00"), "THB", "INV-456", "corr-789");

        assertThat(event.getNoteId()).isEqualTo("note-123");
        assertThat(event.getCancellationNoteNumber()).isEqualTo("CN001");
        assertThat(event.getTotal()).isEqualByComparingTo("1000.00");
        assertThat(event.getCurrency()).isEqualTo("THB");
        assertThat(event.getCancelledInvoiceNumber()).isEqualTo("INV-456");
        assertThat(event.getCorrelationId()).isEqualTo("corr-789");
        assertThat(event.getSource()).isEqualTo("cancellationnote-processing-service");
        assertThat(event.getTraceType()).isEqualTo("CANCELLATION_NOTE_PROCESSED");
    }

    @Test
    @DisplayName("getEventType returns correct event type")
    void getEventType_returnsCorrectEventType() {
        CancellationNoteProcessedEvent event = new CancellationNoteProcessedEvent(
                "note-1", "CN1", BigDecimal.ZERO, "THB", "INV-1", "corr-1");

        assertThat(event.getEventType()).isEqualTo("cancellationnote.processed");
    }

    @Test
    @DisplayName("JsonCreator constructor sets all fields from properties")
    void jsonCreatorConstructor_setsAllFieldsFromProperties() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        CancellationNoteProcessedEvent event = new CancellationNoteProcessedEvent(
                eventId, now, "cancellationnote.processed", 1,
                "saga-1", "corr-1",
                "cancellationnote-processing-service", "CANCELLATION_NOTE_PROCESSED",
                "{\"key\":\"value\"}",
                "note-999", "CN999", new BigDecimal("500.50"), "THB", "INV-888");

        assertThat(event.getEventId()).isEqualTo(eventId);
        assertThat(event.getOccurredAt()).isEqualTo(now);
        assertThat(event.getEventType()).isEqualTo("cancellationnote.processed");
        assertThat(event.getVersion()).isEqualTo(1);
        assertThat(event.getSagaId()).isEqualTo("saga-1");
        assertThat(event.getCorrelationId()).isEqualTo("corr-1");
        assertThat(event.getSource()).isEqualTo("cancellationnote-processing-service");
        assertThat(event.getTraceType()).isEqualTo("CANCELLATION_NOTE_PROCESSED");
        assertThat(event.getContext()).isEqualTo("{\"key\":\"value\"}");
        assertThat(event.getNoteId()).isEqualTo("note-999");
        assertThat(event.getCancellationNoteNumber()).isEqualTo("CN999");
        assertThat(event.getTotal()).isEqualByComparingTo("500.50");
        assertThat(event.getCurrency()).isEqualTo("THB");
        assertThat(event.getCancelledInvoiceNumber()).isEqualTo("INV-888");
    }

    @Test
    @DisplayName("JSON serialization and deserialization round-trip via JsonCreator")
    void jsonRoundTrip() throws JsonProcessingException {
        CancellationNoteProcessedEvent original = new CancellationNoteProcessedEvent(
                "note-abc", "CNA01", new BigDecimal("1234.56"), "THB", "INV-DEF", "corr-xyz");

        String json = objectMapper.writeValueAsString(original);
        CancellationNoteProcessedEvent deserialized = objectMapper.readValue(json,
                CancellationNoteProcessedEvent.class);

        assertThat(deserialized.getNoteId()).isEqualTo("note-abc");
        assertThat(deserialized.getCancellationNoteNumber()).isEqualTo("CNA01");
        assertThat(deserialized.getTotal()).isEqualByComparingTo("1234.56");
        assertThat(deserialized.getCurrency()).isEqualTo("THB");
        assertThat(deserialized.getCancelledInvoiceNumber()).isEqualTo("INV-DEF");
        assertThat(deserialized.getCorrelationId()).isEqualTo("corr-xyz");
        assertThat(deserialized.getEventType()).isEqualTo("cancellationnote.processed");
        assertThat(deserialized.getSource()).isEqualTo("cancellationnote-processing-service");
        assertThat(deserialized.getTraceType()).isEqualTo("CANCELLATION_NOTE_PROCESSED");
    }

    @Test
    @DisplayName("simple constructor sagaId and context are null")
    void simpleConstructor_sagaIdAndContextAreNull() {
        CancellationNoteProcessedEvent event = new CancellationNoteProcessedEvent(
                "note-1", "CN1", BigDecimal.ZERO, "THB", "INV-1", "corr-1");

        assertThat(event.getSagaId()).isNull();
        assertThat(event.getContext()).isNull();
    }

    @Test
    @DisplayName("simple constructor generates eventId via super()")
    void simpleConstructor_generatesEventIdViaSuper() {
        CancellationNoteProcessedEvent event = new CancellationNoteProcessedEvent(
                "note-1", "CN1", BigDecimal.ZERO, "THB", "INV-1", "corr-1");

        assertThat(event.getEventId()).isNotNull();
    }
}
