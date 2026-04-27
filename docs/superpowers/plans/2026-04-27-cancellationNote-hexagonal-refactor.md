# Cancellation Note Processing Service — Hexagonal DDD Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `cancellationnote-processing-service` to full hexagonal DDD parity with `taxinvoice-processing-service` — matching package layout, port interfaces, adapter placement, idempotency, race-condition handling, metrics, and unit tests.

**Architecture:** Ports-and-adapters (hexagonal) with DDD layers: domain ports define contracts, application services implement use cases via those ports, infrastructure adapters wire Spring/JPA/Kafka. New files are created first; old files deleted in the final task once all tests pass.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, JPA/H2, Micrometer, Mockito/JUnit 5, Flyway.

**Spec:** `docs/superpowers/specs/2026-04-27-cancellationNote-refactor-design.md`

> **Note:** This plan is written in batches. Tasks 1–4 cover the domain layer, application ports, config, and persistence adapter. Additional tasks will be added in subsequent batches.

---

## File Map

| New path | Origin | Change |
|----------|--------|--------|
| `domain/port/out/ProcessedCancellationNoteRepository.java` | `domain/repository/` | Move + repackage |
| `domain/port/out/CancellationNoteParserPort.java` | `domain/service/CancellationNoteParserService.java` | Rename + `parse()` + factory methods |
| `domain/event/CancellationNoteProcessedDomainEvent.java` | `domain/event/CancellationNoteProcessedEvent.java` | Rename to record |
| `application/port/in/ProcessCancellationNoteUseCase.java` | — | New |
| `application/port/in/CompensateCancellationNoteUseCase.java` | — | New |
| `application/port/out/SagaReplyPort.java` | — | New |
| `application/port/out/CancellationNoteEventPublishingPort.java` | — | New |
| `application/dto/event/CancellationNoteProcessedEvent.java` | `domain/event/CancellationNoteProcessedEvent.java` | Move to application layer |
| `application/service/CancellationNoteProcessingService.java` | same | Refactor (Task 8) |
| `infrastructure/adapter/in/messaging/dto/ProcessCancellationNoteCommand.java` | `domain/event/` | Move + `SagaStep` enum + getters |
| `infrastructure/adapter/in/messaging/dto/CompensateCancellationNoteCommand.java` | `domain/event/` | Move + `SagaStep` enum + getters |
| `infrastructure/adapter/in/messaging/SagaCommandHandler.java` | `application/service/` | Move + use ports |
| `infrastructure/adapter/in/messaging/SagaRouteConfig.java` | `infrastructure/config/` | Move + `KafkaTopicsProperties` + `RAW()` |
| `infrastructure/adapter/out/messaging/dto/CancellationNoteReplyEvent.java` | `domain/event/` | Move + `SagaStep` |
| `infrastructure/adapter/out/messaging/SagaReplyPublisher.java` | `infrastructure/messaging/` | Move + `REQUIRES_NEW` fix |
| `infrastructure/adapter/out/messaging/CancellationNoteEventPublisher.java` | `infrastructure/messaging/EventPublisher.java` | Rename + port interface |
| `infrastructure/adapter/out/messaging/HeaderSerializer.java` | — | New |
| `infrastructure/adapter/out/parsing/CancellationNoteParserServiceImpl.java` | `infrastructure/service/` | Move + implements `CancellationNoteParserPort` |
| `infrastructure/adapter/out/persistence/ProcessedCancellationNoteEntity.java` | `infrastructure/persistence/` | Move + unique constraint annotation |
| `infrastructure/adapter/out/persistence/CancellationNoteLineItemEntity.java` | `infrastructure/persistence/` | Move |
| `infrastructure/adapter/out/persistence/CancellationNotePartyEntity.java` | `infrastructure/persistence/` | Move |
| `infrastructure/adapter/out/persistence/ProcessedCancellationNoteMapper.java` | — | New (extracted from RepositoryImpl) |
| `infrastructure/adapter/out/persistence/ProcessedCancellationNoteRepositoryImpl.java` | `infrastructure/persistence/` | Move + delegate to mapper |
| `infrastructure/adapter/out/persistence/JpaProcessedCancellationNoteRepository.java` | `infrastructure/persistence/` | Move |
| `infrastructure/adapter/out/persistence/outbox/OutboxEventEntity.java` | `infrastructure/persistence/outbox/` | Move |
| `infrastructure/adapter/out/persistence/outbox/SpringDataOutboxRepository.java` | `infrastructure/persistence/outbox/` | Move |
| `infrastructure/adapter/out/persistence/outbox/JpaOutboxEventRepository.java` | `infrastructure/persistence/outbox/` | Move |
| `infrastructure/adapter/out/persistence/outbox/OutboxCleanupScheduler.java` | — | New |
| `infrastructure/config/KafkaTopicsProperties.java` | — | New |
| `infrastructure/config/OutboxConfig.java` | same | Add `@EnableConfigurationProperties` |
| `db/migration/V3__Add_unique_constraint_source_note_id.sql` | — | New |
| `application.yml` | same | Add camel retry / consumers / outbox config |
| `application-test.yml` | same | Fix YAML indent bug + `flyway.enabled: false` |

**Files deleted in Task 11 (after all tests pass):**
`domain/repository/`, `domain/service/`, `domain/event/CancellationNoteProcessedEvent.java`, `domain/event/ProcessCancellationNoteCommand.java`, `domain/event/CompensateCancellationNoteCommand.java`, `domain/event/CancellationNoteReplyEvent.java`, `application/service/SagaCommandHandler.java`, `infrastructure/config/SagaRouteConfig.java`, `infrastructure/messaging/`, `infrastructure/service/`, `infrastructure/persistence/`

---

## Task 1: Domain ports + domain event rename

**Files:**
- Create: `src/main/java/com/wpanther/cancellationnote/processing/domain/port/out/CancellationNoteParserPort.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/domain/port/out/ProcessedCancellationNoteRepository.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/domain/event/CancellationNoteProcessedDomainEvent.java`

- [ ] **Step 1: Create `CancellationNoteParserPort`**

```java
// src/main/java/com/wpanther/cancellationnote/processing/domain/port/out/CancellationNoteParserPort.java
package com.wpanther.cancellationnote.processing.domain.port.out;

import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;

public interface CancellationNoteParserPort {

    ProcessedCancellationNote parse(String xmlContent, String sourceNoteId)
            throws CancellationNoteParsingException;

    class CancellationNoteParsingException extends Exception {
        public CancellationNoteParsingException(String message) {
            super(message);
        }

        public CancellationNoteParsingException(String message, Throwable cause) {
            super(message, cause);
        }

        public static CancellationNoteParsingException forEmpty() {
            return new CancellationNoteParsingException("XML content is null or empty");
        }

        public static CancellationNoteParsingException forOversized(int byteSize, int limitBytes) {
            return new CancellationNoteParsingException(
                "XML payload too large: " + byteSize + " bytes (limit " + limitBytes + " bytes)");
        }

        public static CancellationNoteParsingException forTimeout(long timeoutMs) {
            return new CancellationNoteParsingException(
                "XML parsing timed out after " + timeoutMs + " ms — possible malformed input");
        }

        public static CancellationNoteParsingException forInterrupted() {
            return new CancellationNoteParsingException("XML parsing was interrupted");
        }

        public static CancellationNoteParsingException forUnmarshal(Throwable cause) {
            return new CancellationNoteParsingException("XML parsing failed: " + cause.getMessage(), cause);
        }

        public static CancellationNoteParsingException forUnexpectedRootElement(String className) {
            return new CancellationNoteParsingException("Unexpected root element: " + className);
        }
    }
}
```

- [ ] **Step 2: Create `ProcessedCancellationNoteRepository` in `domain/port/out/`**

```java
// src/main/java/com/wpanther/cancellationnote/processing/domain/port/out/ProcessedCancellationNoteRepository.java
package com.wpanther.cancellationnote.processing.domain.port.out;

import com.wpanther.cancellationnote.processing.domain.model.CancellationNoteId;
import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;

import java.util.Optional;

public interface ProcessedCancellationNoteRepository {

    ProcessedCancellationNote save(ProcessedCancellationNote note);

    Optional<ProcessedCancellationNote> findById(CancellationNoteId id);

    Optional<ProcessedCancellationNote> findByCancellationNoteNumber(String cancellationNoteNumber);

    Optional<ProcessedCancellationNote> findBySourceNoteId(String sourceNoteId);

    void deleteById(CancellationNoteId id);
}
```

- [ ] **Step 3: Create `CancellationNoteProcessedDomainEvent` record**

```java
// src/main/java/com/wpanther/cancellationnote/processing/domain/event/CancellationNoteProcessedDomainEvent.java
package com.wpanther.cancellationnote.processing.domain.event;

import com.wpanther.cancellationnote.processing.domain.model.Money;

import java.time.Instant;

public record CancellationNoteProcessedDomainEvent(
    String noteId,
    String cancellationNoteNumber,
    Money total,
    String cancelledInvoiceNumber,
    String sagaId,
    String correlationId,
    Instant occurredAt
) {
    public static CancellationNoteProcessedDomainEvent of(
            String noteId,
            String cancellationNoteNumber,
            Money total,
            String cancelledInvoiceNumber,
            String sagaId,
            String correlationId) {
        return new CancellationNoteProcessedDomainEvent(
            noteId, cancellationNoteNumber, total, cancelledInvoiceNumber,
            sagaId, correlationId, Instant.now());
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && git add src/main/java/com/wpanther/cancellationnote/processing/domain/port/ \
             src/main/java/com/wpanther/cancellationnote/processing/domain/event/CancellationNoteProcessedDomainEvent.java \
  && git commit -m "refactor: add domain/port/out interfaces and CancellationNoteProcessedDomainEvent record"
```

---

## Task 2: Application port interfaces + DTO

**Files:**
- Create: `src/main/java/com/wpanther/cancellationnote/processing/application/port/in/ProcessCancellationNoteUseCase.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/application/port/in/CompensateCancellationNoteUseCase.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/application/port/out/SagaReplyPort.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/application/port/out/CancellationNoteEventPublishingPort.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/application/dto/event/CancellationNoteProcessedEvent.java`

- [ ] **Step 1: Create `ProcessCancellationNoteUseCase`**

```java
// src/main/java/com/wpanther/cancellationnote/processing/application/port/in/ProcessCancellationNoteUseCase.java
package com.wpanther.cancellationnote.processing.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

public interface ProcessCancellationNoteUseCase {

    void process(String documentId, String xmlContent,
                 String sagaId, SagaStep sagaStep, String correlationId)
            throws CancellationNoteProcessingException;

    class CancellationNoteProcessingException extends Exception {
        public CancellationNoteProcessingException(String message) {
            super(message);
        }

        public CancellationNoteProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

- [ ] **Step 2: Create `CompensateCancellationNoteUseCase`**

```java
// src/main/java/com/wpanther/cancellationnote/processing/application/port/in/CompensateCancellationNoteUseCase.java
package com.wpanther.cancellationnote.processing.application.port.in;

import com.wpanther.saga.domain.enums.SagaStep;

public interface CompensateCancellationNoteUseCase {

    void compensate(String documentId, String sagaId,
                    SagaStep sagaStep, String correlationId);

    class CancellationNoteCompensationException extends RuntimeException {
        public CancellationNoteCompensationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

- [ ] **Step 3: Create `SagaReplyPort`**

```java
// src/main/java/com/wpanther/cancellationnote/processing/application/port/out/SagaReplyPort.java
package com.wpanther.cancellationnote.processing.application.port.out;

import com.wpanther.saga.domain.enums.SagaStep;

public interface SagaReplyPort {

    void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId);

    // Implemented with REQUIRES_NEW propagation — commits in its own transaction
    // even when the caller's transaction is marked ROLLBACK_ONLY.
    void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage);

    void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId);
}
```

- [ ] **Step 4: Create `CancellationNoteEventPublishingPort`**

```java
// src/main/java/com/wpanther/cancellationnote/processing/application/port/out/CancellationNoteEventPublishingPort.java
package com.wpanther.cancellationnote.processing.application.port.out;

import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedDomainEvent;

public interface CancellationNoteEventPublishingPort {

    void publish(CancellationNoteProcessedDomainEvent event);
}
```

- [ ] **Step 5: Create application-layer DTO `CancellationNoteProcessedEvent`**

```java
// src/main/java/com/wpanther/cancellationnote/processing/application/dto/event/CancellationNoteProcessedEvent.java
package com.wpanther.cancellationnote.processing.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class CancellationNoteProcessedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "cancellationnote.processed";

    @JsonProperty("noteId")
    private final String noteId;

    @JsonProperty("cancellationNoteNumber")
    private final String cancellationNoteNumber;

    @JsonProperty("total")
    private final BigDecimal total;

    @JsonProperty("currency")
    private final String currency;

    @JsonProperty("cancelledInvoiceNumber")
    private final String cancelledInvoiceNumber;

    @JsonProperty("correlationId")
    private final String correlationId;

    public CancellationNoteProcessedEvent(String noteId, String cancellationNoteNumber,
                                          BigDecimal total, String currency,
                                          String cancelledInvoiceNumber, String correlationId) {
        super();
        this.noteId = noteId;
        this.cancellationNoteNumber = cancellationNoteNumber;
        this.total = total;
        this.currency = currency;
        this.cancelledInvoiceNumber = cancelledInvoiceNumber;
        this.correlationId = correlationId;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @JsonCreator
    public CancellationNoteProcessedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("noteId") String noteId,
            @JsonProperty("cancellationNoteNumber") String cancellationNoteNumber,
            @JsonProperty("total") BigDecimal total,
            @JsonProperty("currency") String currency,
            @JsonProperty("cancelledInvoiceNumber") String cancelledInvoiceNumber,
            @JsonProperty("correlationId") String correlationId) {
        super(eventId, occurredAt, eventType, version);
        this.noteId = noteId;
        this.cancellationNoteNumber = cancellationNoteNumber;
        this.total = total;
        this.currency = currency;
        this.cancelledInvoiceNumber = cancelledInvoiceNumber;
        this.correlationId = correlationId;
    }
}
```

- [ ] **Step 6: Verify compilation**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && git add src/main/java/com/wpanther/cancellationnote/processing/application/ \
  && git commit -m "refactor: add application port interfaces and CancellationNoteProcessedEvent DTO"
```

---

## Task 3: Typed Kafka config + V3 migration + YAML fixes

**Files:**
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/config/KafkaTopicsProperties.java`
- Modify: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/config/OutboxConfig.java`
- Create: `src/main/resources/db/migration/V3__Add_unique_constraint_source_note_id.sql`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

- [ ] **Step 1: Create `KafkaTopicsProperties`**

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/config/KafkaTopicsProperties.java
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
```

- [ ] **Step 2: Update `OutboxConfig` to register `KafkaTopicsProperties`**

Add `@EnableConfigurationProperties(KafkaTopicsProperties.class)` to the class:

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/config/OutboxConfig.java
package com.wpanther.cancellationnote.processing.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.cancellationnote.processing.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.wpanther.cancellationnote.processing.infrastructure.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class OutboxConfig {

    @Bean
    @ConditionalOnMissingBean(OutboxEventRepository.class)
    public OutboxEventRepository outboxEventRepository(SpringDataOutboxRepository springRepository) {
        return new JpaOutboxEventRepository(springRepository);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxService.class)
    public OutboxService outboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        return new OutboxService(repository, objectMapper);
    }
}
```

Note: `OutboxConfig` still references `JpaOutboxEventRepository` and `SpringDataOutboxRepository` in their old package. This import will be updated in Task 4 when those classes move.

- [ ] **Step 3: Create V3 migration**

```sql
-- src/main/resources/db/migration/V3__Add_unique_constraint_source_note_id.sql
ALTER TABLE processed_cancellation_notes
ADD CONSTRAINT uq_processed_cancellation_notes_source_note_id
UNIQUE (source_note_id);

DROP INDEX IF EXISTS idx_cancellation_source_note_id;
```

- [ ] **Step 4: Update `application.yml` — add camel retry, consumers, and outbox cleanup config**

Append these keys under `app:` (after the existing `kafka:` block):

```yaml
# src/main/resources/application.yml  — add under the app: key
app:
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
    topics:
      cancellation-note-processed: cancellationnote.processed
      dlq: cancellationnote.processing.dlq
      saga-command-cancellation-note: saga.command.cancellation-note
      saga-compensation-cancellation-note: saga.compensation.cancellation-note
      saga-reply-cancellation-note: saga.reply.cancellation-note
    consumers:
      max-poll-records: ${KAFKA_MAX_POLL_RECORDS:100}
      count: ${KAFKA_CONSUMERS_COUNT:3}
  camel:
    retry:
      max-redeliveries: ${CAMEL_MAX_REDELIVERIES:3}
      redelivery-delay-ms: ${CAMEL_REDELIVERY_DELAY_MS:1000}
      backoff-multiplier: ${CAMEL_BACKOFF_MULTIPLIER:2.0}
      max-redelivery-delay-ms: ${CAMEL_MAX_REDELIVERY_DELAY_MS:10000}
  outbox:
    cleanup:
      retention-days: ${OUTBOX_RETENTION_DAYS:7}
      cron: "0 0 2 * * *"
```

Replace the full `app:` section in `application.yml` with the above (the old `topics:` keys had incorrect binding names — `cancellationnote-processed` instead of `cancellation-note-processed`). The complete updated `app:` block:

```yaml
app:
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
    topics:
      cancellation-note-processed: cancellationnote.processed
      dlq: cancellationnote.processing.dlq
      saga-command-cancellation-note: saga.command.cancellation-note
      saga-compensation-cancellation-note: saga.compensation.cancellation-note
      saga-reply-cancellation-note: saga.reply.cancellation-note
    consumers:
      max-poll-records: ${KAFKA_MAX_POLL_RECORDS:100}
      count: ${KAFKA_CONSUMERS_COUNT:3}
  camel:
    retry:
      max-redeliveries: ${CAMEL_MAX_REDELIVERIES:3}
      redelivery-delay-ms: ${CAMEL_REDELIVERY_DELAY_MS:1000}
      backoff-multiplier: ${CAMEL_BACKOFF_MULTIPLIER:2.0}
      max-redelivery-delay-ms: ${CAMEL_MAX_REDELIVERY_DELAY_MS:10000}
  outbox:
    cleanup:
      retention-days: ${OUTBOX_RETENTION_DAYS:7}
      cron: "0 0 2 * * *"
```

- [ ] **Step 5: Fix `application-test.yml`**

The current file has a YAML indentation bug under `camel:` and is missing `flyway.enabled: false`. Replace the entire file with:

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
        format_sql: true

  flyway:
    enabled: false

camel:
  springboot:
    name: cancellationnote-processing-camel-test
  dataformat:
    jackson:
      auto-discover-object-mapper: true

app:
  kafka:
    bootstrap-servers: localhost:9093
    topics:
      cancellation-note-processed: cancellationnote.processed.test
      dlq: cancellationnote.processing.dlq.test
      saga-command-cancellation-note: saga.command.cancellation-note.test
      saga-compensation-cancellation-note: saga.compensation.cancellation-note.test
      saga-reply-cancellation-note: saga.reply.cancellation-note.test
    consumers:
      max-poll-records: 10
      count: 1
  camel:
    retry:
      max-redeliveries: 1
      redelivery-delay-ms: 100
      backoff-multiplier: 1.0
      max-redelivery-delay-ms: 200
  outbox:
    cleanup:
      retention-days: 7
      cron: "0 0 2 * * *"

logging:
  level:
    root: WARN
    com.wpanther.cancellationnote.processing: INFO
    org.apache.camel: WARN
```

- [ ] **Step 6: Verify compilation**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && git add src/main/java/com/wpanther/cancellationnote/processing/infrastructure/config/KafkaTopicsProperties.java \
             src/main/java/com/wpanther/cancellationnote/processing/infrastructure/config/OutboxConfig.java \
             src/main/resources/db/migration/V3__Add_unique_constraint_source_note_id.sql \
             src/main/resources/application.yml \
             src/test/resources/application-test.yml \
  && git commit -m "refactor: add KafkaTopicsProperties, V3 unique constraint migration, fix application-test.yml"
```

---

## Task 4: Persistence adapter — entities, mapper, repository, outbox, scheduler

**Files:**
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteEntity.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/CancellationNoteLineItemEntity.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/CancellationNotePartyEntity.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteMapper.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteRepositoryImpl.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/JpaProcessedCancellationNoteRepository.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/OutboxEventEntity.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/SpringDataOutboxRepository.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/JpaOutboxEventRepository.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/OutboxCleanupScheduler.java`
- Test: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteMapperTest.java`
- Test: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteEntityTest.java`
- Test: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/CancellationNoteLineItemEntityTest.java`
- Test: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/CancellationNotePartyEntityTest.java`
- Test: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteRepositoryImplTest.java`
- Test: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/OutboxEventEntityTest.java`
- Test: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/JpaOutboxEventRepositoryTest.java`
- Test: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/OutboxCleanupSchedulerTest.java`

- [ ] **Step 1: Write the mapper test first**

```java
// src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteMapperTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedCancellationNoteMapperTest {

    private ProcessedCancellationNoteMapper mapper;

    private ProcessedCancellationNote buildDomain() {
        Party seller = Party.of("Seller Co", TaxIdentifier.of("1234567890", "VAT"),
                Address.of("1 Main St", "Bangkok", "10110", "TH"), "seller@test.com");
        Party buyer = Party.of("Buyer Co", TaxIdentifier.of("0987654321", "VAT"),
                Address.of("2 Side Rd", "Chiang Mai", "50000", "TH"), "buyer@test.com");
        LineItem item = new LineItem("Widget", 5,
                Money.of(new BigDecimal("200.00"), "THB"), new BigDecimal("7.00"));
        return ProcessedCancellationNote.builder()
                .id(CancellationNoteId.generate())
                .sourceNoteId("intake-001")
                .cancellationNoteNumber("CN-001")
                .issueDate(LocalDate.of(2025, 1, 15))
                .cancellationDate(LocalDate.of(2025, 1, 20))
                .seller(seller)
                .buyer(buyer)
                .items(List.of(item))
                .currency("THB")
                .cancelledInvoiceNumber("INV-100")
                .originalXml("<xml/>")
                .build();
    }

    @BeforeEach
    void setUp() {
        mapper = new ProcessedCancellationNoteMapper();
    }

    @Test
    void toEntity_mapsAllScalarFields() {
        ProcessedCancellationNote domain = buildDomain();
        ProcessedCancellationNoteEntity entity = mapper.toEntity(domain);

        assertEquals(domain.id().value(), entity.getId());
        assertEquals("intake-001", entity.getSourceNoteId());
        assertEquals("CN-001", entity.getCancellationNoteNumber());
        assertEquals(LocalDate.of(2025, 1, 15), entity.getIssueDate());
        assertEquals(LocalDate.of(2025, 1, 20), entity.getCancellationDate());
        assertEquals("INV-100", entity.getCancelledInvoiceNumber());
        assertEquals("THB", entity.getCurrency());
        assertEquals("<xml/>", entity.getOriginalXml());
        assertEquals(ProcessingStatus.PENDING, entity.getStatus());
    }

    @Test
    void toEntity_mapsCalculatedTotals() {
        ProcessedCancellationNote domain = buildDomain();
        ProcessedCancellationNoteEntity entity = mapper.toEntity(domain);

        assertEquals(domain.getSubtotal().amount(), entity.getSubtotal());
        assertEquals(domain.getTotalTax().amount(), entity.getTotalTax());
        assertEquals(domain.getTotal().amount(), entity.getTotal());
    }

    @Test
    void toEntity_createsTwoParties() {
        ProcessedCancellationNoteEntity entity = mapper.toEntity(buildDomain());
        assertEquals(2, entity.getParties().size());
        assertTrue(entity.getParties().stream()
                .anyMatch(p -> p.getPartyType() == CancellationNotePartyEntity.PartyType.SELLER));
        assertTrue(entity.getParties().stream()
                .anyMatch(p -> p.getPartyType() == CancellationNotePartyEntity.PartyType.BUYER));
    }

    @Test
    void toEntity_createsLineItems() {
        ProcessedCancellationNoteEntity entity = mapper.toEntity(buildDomain());
        assertEquals(1, entity.getLineItems().size());
        CancellationNoteLineItemEntity lineItem = entity.getLineItems().get(0);
        assertEquals("Widget", lineItem.getDescription());
        assertEquals(5, lineItem.getQuantity());
        assertEquals(new BigDecimal("200.00"), lineItem.getUnitPrice());
    }

    @Test
    void toDomain_roundTrip() {
        ProcessedCancellationNote original = buildDomain();
        ProcessedCancellationNoteEntity entity = mapper.toEntity(original);
        // Give the entity a real UUID id and wire relationships
        entity.setId(original.id().value());
        entity.getParties().forEach(p -> p.setCancellationNote(entity));
        entity.getLineItems().forEach(li -> li.setCancellationNote(entity));

        ProcessedCancellationNote restored = mapper.toDomain(entity);

        assertEquals(original.id().value(), restored.id().value());
        assertEquals("intake-001", restored.sourceNoteId());
        assertEquals("CN-001", restored.cancellationNoteNumber());
        assertEquals("THB", restored.currency());
        assertEquals("INV-100", restored.cancelledInvoiceNumber());
        assertEquals(1, restored.items().size());
        assertNotNull(restored.seller());
        assertNotNull(restored.buyer());
    }

    @Test
    void toDomain_throwsWhenSellerMissing() {
        ProcessedCancellationNoteEntity entity = mapper.toEntity(buildDomain());
        entity.setId(UUID.randomUUID());
        entity.getParties().removeIf(
                p -> p.getPartyType() == CancellationNotePartyEntity.PartyType.SELLER);
        assertThrows(IllegalStateException.class, () -> mapper.toDomain(entity));
    }

    @Test
    void toDomain_throwsWhenBuyerMissing() {
        ProcessedCancellationNoteEntity entity = mapper.toEntity(buildDomain());
        entity.setId(UUID.randomUUID());
        entity.getParties().removeIf(
                p -> p.getPartyType() == CancellationNotePartyEntity.PartyType.BUYER);
        assertThrows(IllegalStateException.class, () -> mapper.toDomain(entity));
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure (mapper not created yet)**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test -Dtest=ProcessedCancellationNoteMapperTest -pl . 2>&1 | tail -20
```

Expected: `COMPILATION ERROR` — `ProcessedCancellationNoteMapper` does not exist yet.

- [ ] **Step 3: Create entity classes in new package**

`ProcessedCancellationNoteEntity` — copy from old package, update `@Table` to add unique constraint annotation, update `package` declaration:

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteEntity.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.domain.model.ProcessingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(
    name = "processed_cancellation_notes",
    indexes = {
        @Index(name = "idx_cancellation_note_number", columnList = "cancellation_note_number"),
        @Index(name = "idx_cancellation_status", columnList = "status"),
        @Index(name = "idx_cancellation_issue_date", columnList = "issue_date")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_processed_cancellation_notes_source_note_id",
            columnNames = "source_note_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedCancellationNoteEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "source_note_id", nullable = false, length = 100)
    private String sourceNoteId;

    @Column(name = "cancellation_note_number", nullable = false, length = 50)
    private String cancellationNoteNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "cancellation_date", nullable = false)
    private LocalDate cancellationDate;

    @Column(name = "cancelled_invoice_number", nullable = false, length = 50)
    private String cancelledInvoiceNumber;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalTax;

    @Column(name = "total", nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "original_xml", nullable = false, columnDefinition = "TEXT")
    private String originalXml;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProcessingStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "cancellationNote", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<CancellationNotePartyEntity> parties = new HashSet<>();

    @OneToMany(mappedBy = "cancellationNote", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    @Builder.Default
    private List<CancellationNoteLineItemEntity> lineItems = new ArrayList<>();

    public void addParty(CancellationNotePartyEntity party) {
        parties.add(party);
        party.setCancellationNote(this);
    }

    public void addLineItem(CancellationNoteLineItemEntity lineItem) {
        lineItems.add(lineItem);
        lineItem.setCancellationNote(this);
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
```

`CancellationNoteLineItemEntity` — copy from old package, update package:

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/CancellationNoteLineItemEntity.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cancellation_note_line_items",
    indexes = { @Index(name = "idx_cn_line_item_note", columnList = "cancellation_note_id") })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationNoteLineItemEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancellation_note_id", nullable = false)
    private ProcessedCancellationNoteEntity cancellationNote;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
```

`CancellationNotePartyEntity` — read the old file and copy to new package:

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/CancellationNotePartyEntity.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "cancellation_note_parties",
    indexes = {
        @Index(name = "idx_cn_party_note", columnList = "cancellation_note_id"),
        @Index(name = "idx_cn_party_type", columnList = "party_type")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CancellationNotePartyEntity {

    public enum PartyType { SELLER, BUYER }

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancellation_note_id", nullable = false)
    private ProcessedCancellationNoteEntity cancellationNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 10)
    private PartyType partyType;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "tax_id_scheme", length = 20)
    private String taxIdScheme;

    @Column(name = "street_address", length = 500)
    private String streetAddress;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "email", length = 200)
    private String email;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
```

- [ ] **Step 4: Create `ProcessedCancellationNoteMapper`**

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteMapper.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.domain.model.*;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.CancellationNotePartyEntity.PartyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ProcessedCancellationNoteMapper {

    public ProcessedCancellationNoteEntity toEntity(ProcessedCancellationNote domain) {
        ProcessedCancellationNoteEntity entity = ProcessedCancellationNoteEntity.builder()
                .id(domain.id() != null ? domain.id().value() : UUID.randomUUID())
                .sourceNoteId(domain.sourceNoteId())
                .cancellationNoteNumber(domain.cancellationNoteNumber())
                .issueDate(domain.issueDate())
                .cancellationDate(domain.cancellationDate())
                .cancelledInvoiceNumber(domain.cancelledInvoiceNumber())
                .currency(domain.currency())
                .subtotal(domain.getSubtotal().amount())
                .totalTax(domain.getTotalTax().amount())
                .total(domain.getTotal().amount())
                .originalXml(domain.originalXml())
                .status(domain.status())
                .errorMessage(domain.errorMessage())
                .createdAt(domain.createdAt())
                .completedAt(domain.completedAt())
                .build();

        // Map seller
        entity.addParty(toPartyEntity(domain.seller(), PartyType.SELLER, entity));

        // Map buyer
        entity.addParty(toPartyEntity(domain.buyer(), PartyType.BUYER, entity));

        // Map line items
        int lineNumber = 1;
        for (LineItem item : domain.items()) {
            entity.addLineItem(toLineItemEntity(item, lineNumber++, entity));
        }

        return entity;
    }

    public ProcessedCancellationNote toDomain(ProcessedCancellationNoteEntity entity) {
        Party seller = null;
        Party buyer = null;

        for (CancellationNotePartyEntity partyEntity : entity.getParties()) {
            if (partyEntity == null) continue;
            Party party = toPartyDomain(partyEntity);
            if (partyEntity.getPartyType() == PartyType.SELLER) {
                seller = party;
            } else if (partyEntity.getPartyType() == PartyType.BUYER) {
                buyer = party;
            }
        }

        if (seller == null) {
            throw new IllegalStateException(
                "No SELLER party found for cancellation note " + entity.getId());
        }
        if (buyer == null) {
            throw new IllegalStateException(
                "No BUYER party found for cancellation note " + entity.getId());
        }

        List<LineItem> items = new ArrayList<>();
        for (CancellationNoteLineItemEntity itemEntity : entity.getLineItems()) {
            if (itemEntity != null) {
                items.add(toLineItemDomain(itemEntity, entity.getCurrency()));
            }
        }

        return ProcessedCancellationNote.builder()
                .id(CancellationNoteId.of(entity.getId()))
                .sourceNoteId(entity.getSourceNoteId())
                .cancellationNoteNumber(entity.getCancellationNoteNumber())
                .issueDate(entity.getIssueDate())
                .cancellationDate(entity.getCancellationDate())
                .seller(seller)
                .buyer(buyer)
                .items(items)
                .currency(entity.getCurrency())
                .cancelledInvoiceNumber(entity.getCancelledInvoiceNumber())
                .originalXml(entity.getOriginalXml())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .completedAt(entity.getCompletedAt())
                .errorMessage(entity.getErrorMessage())
                .build();
    }

    private CancellationNotePartyEntity toPartyEntity(Party domain, PartyType partyType,
                                                       ProcessedCancellationNoteEntity parent) {
        CancellationNotePartyEntity entity = new CancellationNotePartyEntity();
        entity.setId(UUID.randomUUID());
        entity.setCancellationNote(parent);
        entity.setPartyType(partyType);
        entity.setName(domain.name());
        entity.setTaxId(domain.taxIdentifier() != null ? domain.taxIdentifier().value() : null);
        entity.setTaxIdScheme(domain.taxIdentifier() != null ? domain.taxIdentifier().scheme() : null);
        entity.setStreetAddress(domain.address() != null ? domain.address().streetAddress() : null);
        entity.setCity(domain.address() != null ? domain.address().city() : null);
        entity.setPostalCode(domain.address() != null ? domain.address().postalCode() : null);
        entity.setCountry(domain.address() != null ? domain.address().country() : null);
        entity.setEmail(domain.email());
        return entity;
    }

    private Party toPartyDomain(CancellationNotePartyEntity entity) {
        TaxIdentifier taxId = entity.getTaxId() != null
            ? TaxIdentifier.of(entity.getTaxId(), entity.getTaxIdScheme())
            : null;
        Address address = entity.getCountry() != null
            ? Address.of(entity.getStreetAddress(), entity.getCity(),
                         entity.getPostalCode(), entity.getCountry())
            : null;
        return Party.of(entity.getName(), taxId, address, entity.getEmail());
    }

    private CancellationNoteLineItemEntity toLineItemEntity(LineItem domain, int lineNumber,
                                                             ProcessedCancellationNoteEntity parent) {
        CancellationNoteLineItemEntity entity = new CancellationNoteLineItemEntity();
        entity.setId(UUID.randomUUID());
        entity.setCancellationNote(parent);
        entity.setLineNumber(lineNumber);
        entity.setDescription(domain.description());
        entity.setQuantity(domain.quantity());
        entity.setUnitPrice(domain.unitPrice().amount());
        entity.setTaxRate(domain.taxRate());
        entity.setLineTotal(domain.getLineTotal().amount());
        entity.setTaxAmount(domain.getTaxAmount().amount());
        return entity;
    }

    private LineItem toLineItemDomain(CancellationNoteLineItemEntity entity, String currency) {
        Money unitPrice = Money.of(entity.getUnitPrice(), currency);
        return new LineItem(entity.getDescription(), entity.getQuantity(),
                            unitPrice, entity.getTaxRate());
    }
}
```

- [ ] **Step 5: Run mapper test — expect it to pass now**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test -Dtest=ProcessedCancellationNoteMapperTest 2>&1 | tail -15
```

Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: Create entity tests**

```java
// src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteEntityTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.domain.model.ProcessingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedCancellationNoteEntityTest {

    @Test
    void builder_setsAllFields() {
        UUID id = UUID.randomUUID();
        ProcessedCancellationNoteEntity entity = ProcessedCancellationNoteEntity.builder()
                .id(id)
                .sourceNoteId("src-1")
                .cancellationNoteNumber("CN-001")
                .issueDate(LocalDate.of(2025, 1, 1))
                .cancellationDate(LocalDate.of(2025, 1, 10))
                .cancelledInvoiceNumber("INV-001")
                .currency("THB")
                .subtotal(new BigDecimal("1000.00"))
                .totalTax(new BigDecimal("70.00"))
                .total(new BigDecimal("1070.00"))
                .originalXml("<xml/>")
                .status(ProcessingStatus.PENDING)
                .build();

        assertEquals(id, entity.getId());
        assertEquals("CN-001", entity.getCancellationNoteNumber());
        assertEquals(ProcessingStatus.PENDING, entity.getStatus());
    }

    @Test
    void addParty_wiresBackReference() {
        ProcessedCancellationNoteEntity entity = ProcessedCancellationNoteEntity.builder()
                .id(UUID.randomUUID()).sourceNoteId("s").cancellationNoteNumber("CN-1")
                .issueDate(LocalDate.now()).cancellationDate(LocalDate.now())
                .cancelledInvoiceNumber("INV-1").currency("THB")
                .subtotal(BigDecimal.ZERO).totalTax(BigDecimal.ZERO).total(BigDecimal.ZERO)
                .originalXml("<xml/>").status(ProcessingStatus.PENDING).build();

        CancellationNotePartyEntity party = new CancellationNotePartyEntity();
        party.setId(UUID.randomUUID());
        party.setPartyType(CancellationNotePartyEntity.PartyType.SELLER);
        party.setName("Test");
        party.setCountry("TH");

        entity.addParty(party);

        assertEquals(1, entity.getParties().size());
        assertSame(entity, party.getCancellationNote());
    }

    @Test
    void addLineItem_wiresBackReference() {
        ProcessedCancellationNoteEntity entity = ProcessedCancellationNoteEntity.builder()
                .id(UUID.randomUUID()).sourceNoteId("s").cancellationNoteNumber("CN-1")
                .issueDate(LocalDate.now()).cancellationDate(LocalDate.now())
                .cancelledInvoiceNumber("INV-1").currency("THB")
                .subtotal(BigDecimal.ZERO).totalTax(BigDecimal.ZERO).total(BigDecimal.ZERO)
                .originalXml("<xml/>").status(ProcessingStatus.PENDING).build();

        CancellationNoteLineItemEntity line = new CancellationNoteLineItemEntity();
        line.setId(UUID.randomUUID());
        line.setLineNumber(1);
        line.setDescription("Item");
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setTaxRate(new BigDecimal("7.00"));
        line.setLineTotal(new BigDecimal("100.00"));
        line.setTaxAmount(new BigDecimal("7.00"));

        entity.addLineItem(line);

        assertEquals(1, entity.getLineItems().size());
        assertSame(entity, line.getCancellationNote());
    }
}
```

```java
// src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/CancellationNoteLineItemEntityTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CancellationNoteLineItemEntityTest {

    @Test
    void setters_workCorrectly() {
        CancellationNoteLineItemEntity entity = new CancellationNoteLineItemEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setLineNumber(1);
        entity.setDescription("Widget");
        entity.setQuantity(3);
        entity.setUnitPrice(new BigDecimal("500.00"));
        entity.setTaxRate(new BigDecimal("7.00"));
        entity.setLineTotal(new BigDecimal("1500.00"));
        entity.setTaxAmount(new BigDecimal("105.00"));

        assertEquals(id, entity.getId());
        assertEquals("Widget", entity.getDescription());
        assertEquals(3, entity.getQuantity());
        assertEquals(new BigDecimal("500.00"), entity.getUnitPrice());
    }
}
```

```java
// src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/CancellationNotePartyEntityTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CancellationNotePartyEntityTest {

    @Test
    void setters_workCorrectly() {
        CancellationNotePartyEntity entity = new CancellationNotePartyEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setPartyType(CancellationNotePartyEntity.PartyType.SELLER);
        entity.setName("Seller Inc");
        entity.setTaxId("1234567890");
        entity.setTaxIdScheme("VAT");
        entity.setCountry("TH");
        entity.setEmail("seller@example.com");

        assertEquals(id, entity.getId());
        assertEquals(CancellationNotePartyEntity.PartyType.SELLER, entity.getPartyType());
        assertEquals("Seller Inc", entity.getName());
        assertEquals("TH", entity.getCountry());
    }

    @Test
    void partyType_hasBothValues() {
        assertNotNull(CancellationNotePartyEntity.PartyType.SELLER);
        assertNotNull(CancellationNotePartyEntity.PartyType.BUYER);
    }
}
```

- [ ] **Step 7: Run entity tests**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test -Dtest="ProcessedCancellationNoteEntityTest,CancellationNoteLineItemEntityTest,CancellationNotePartyEntityTest" 2>&1 | tail -10
```

Expected: All tests pass.

- [ ] **Step 8: Create `JpaProcessedCancellationNoteRepository`**

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/JpaProcessedCancellationNoteRepository.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaProcessedCancellationNoteRepository
        extends JpaRepository<ProcessedCancellationNoteEntity, UUID> {

    @Query("SELECT n FROM ProcessedCancellationNoteEntity n "
         + "LEFT JOIN FETCH n.parties "
         + "LEFT JOIN FETCH n.lineItems "
         + "WHERE n.id = :id")
    Optional<ProcessedCancellationNoteEntity> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT n FROM ProcessedCancellationNoteEntity n "
         + "LEFT JOIN FETCH n.parties "
         + "LEFT JOIN FETCH n.lineItems "
         + "WHERE n.cancellationNoteNumber = :number")
    Optional<ProcessedCancellationNoteEntity> findByCancellationNoteNumberWithDetails(
            @Param("number") String cancellationNoteNumber);

    @Query("SELECT n FROM ProcessedCancellationNoteEntity n "
         + "LEFT JOIN FETCH n.parties "
         + "LEFT JOIN FETCH n.lineItems "
         + "WHERE n.sourceNoteId = :sourceNoteId")
    Optional<ProcessedCancellationNoteEntity> findBySourceNoteIdWithDetails(
            @Param("sourceNoteId") String sourceNoteId);
}
```

- [ ] **Step 9: Create `ProcessedCancellationNoteRepositoryImpl` in new package**

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteRepositoryImpl.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.domain.model.CancellationNoteId;
import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;
import com.wpanther.cancellationnote.processing.domain.port.out.ProcessedCancellationNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessedCancellationNoteRepositoryImpl implements ProcessedCancellationNoteRepository {

    private final JpaProcessedCancellationNoteRepository jpaRepository;
    private final ProcessedCancellationNoteMapper mapper;

    @Override
    @Transactional
    public ProcessedCancellationNote save(ProcessedCancellationNote note) {
        log.debug("Saving cancellation note: {}", note.cancellationNoteNumber());
        ProcessedCancellationNoteEntity entity = mapper.toEntity(note);
        ProcessedCancellationNoteEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedCancellationNote> findById(CancellationNoteId id) {
        return jpaRepository.findByIdWithDetails(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedCancellationNote> findByCancellationNoteNumber(String cancellationNoteNumber) {
        return jpaRepository.findByCancellationNoteNumberWithDetails(cancellationNoteNumber)
                            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedCancellationNote> findBySourceNoteId(String sourceNoteId) {
        return jpaRepository.findBySourceNoteIdWithDetails(sourceNoteId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(CancellationNoteId id) {
        log.info("Deleting cancellation note: {}", id.value());
        jpaRepository.deleteById(id.value());
    }
}
```

- [ ] **Step 10: Create outbox classes in new package**

`OutboxEventEntity` — copy from old package, update package declaration:

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/OutboxEventEntity.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox;
```

Read the content from the old file at `infrastructure/persistence/outbox/OutboxEventEntity.java` and copy it with only the package declaration changed.

`SpringDataOutboxRepository` — copy from old package, update package declaration:

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/SpringDataOutboxRepository.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox;
```

Read the content from `infrastructure/persistence/outbox/SpringDataOutboxRepository.java` and copy with package updated.

`JpaOutboxEventRepository` — copy from old package, update package and import:

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/JpaOutboxEventRepository.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox;
```

Read content from `infrastructure/persistence/outbox/JpaOutboxEventRepository.java` and copy with package updated.

- [ ] **Step 11: Create `OutboxCleanupScheduler`**

```java
// src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/OutboxCleanupScheduler.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Slf4j
public class OutboxCleanupScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final Counter cleanupFailureCounter;

    @Value("${app.outbox.cleanup.retention-days:7}")
    private int retentionDays;

    @Value("${app.outbox.cleanup.cron:0 0 2 * * *}")
    private String cleanupCron;

    public OutboxCleanupScheduler(OutboxEventRepository outboxEventRepository,
                                  MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.cleanupFailureCounter = Counter.builder("outbox.cleanup.failure")
            .description("Number of times the outbox cleanup job failed")
            .register(meterRegistry);
    }

    @PostConstruct
    void logConfiguration() {
        if (retentionDays < 1) {
            throw new IllegalStateException(
                "app.outbox.cleanup.retention-days must be >= 1, got: " + retentionDays);
        }
        log.info("OutboxCleanupScheduler initialized: retention={} days, cron='{}'",
            retentionDays, cleanupCron);
    }

    @Scheduled(cron = "${app.outbox.cleanup.cron:0 0 2 * * *}")
    public void cleanPublishedEvents() {
        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            int deleted = outboxEventRepository.deletePublishedBefore(cutoff);
            log.info("Outbox cleanup: deleted {} published events older than {} days",
                deleted, retentionDays);
        } catch (Exception e) {
            cleanupFailureCounter.increment();
            log.error("Outbox cleanup failed: {}", e.toString());
        }
    }
}
```

- [ ] **Step 12: Update `OutboxConfig` to reference new outbox package**

In `infrastructure/config/OutboxConfig.java`, update the imports from `infrastructure.persistence.outbox` to `infrastructure.adapter.out.persistence.outbox`:

```java
import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox.JpaOutboxEventRepository;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox.SpringDataOutboxRepository;
```

- [ ] **Step 13: Write outbox unit tests**

```java
// src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/OutboxEventEntityTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventEntityTest {

    @Test
    void fromDomain_thenToDomain_roundTrip() {
        OutboxEvent event = new OutboxEvent(
            UUID.randomUUID(), "ProcessedCancellationNote", "note-1",
            "cancellationnote.processed", "{}", "note-1",
            OutboxStatus.PENDING, null, 0, Instant.now(), null);

        OutboxEventEntity entity = OutboxEventEntity.fromDomain(event);
        OutboxEvent restored = entity.toDomain();

        assertEquals(event.getId(), restored.getId());
        assertEquals(event.getAggregateType(), restored.getAggregateType());
        assertEquals(event.getAggregateId(), restored.getAggregateId());
        assertEquals(OutboxStatus.PENDING, restored.getStatus());
    }
}
```

```java
// src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/outbox/OutboxCleanupSchedulerTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupSchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxCleanupScheduler(outboxEventRepository, new SimpleMeterRegistry());
    }

    @Test
    void cleanPublishedEvents_callsDeleteBefore() {
        when(outboxEventRepository.deletePublishedBefore(any(Instant.class))).thenReturn(5);

        scheduler.cleanPublishedEvents();

        verify(outboxEventRepository).deletePublishedBefore(any(Instant.class));
    }

    @Test
    void cleanPublishedEvents_doesNotThrowOnRepositoryException() {
        when(outboxEventRepository.deletePublishedBefore(any(Instant.class)))
            .thenThrow(new RuntimeException("DB error"));

        // Should not propagate the exception
        scheduler.cleanPublishedEvents();

        verify(outboxEventRepository).deletePublishedBefore(any(Instant.class));
    }
}
```

- [ ] **Step 14: Write `ProcessedCancellationNoteRepositoryImplTest` (H2-backed)**

```java
// src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ProcessedCancellationNoteRepositoryImplTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence;

import com.wpanther.cancellationnote.processing.domain.model.*;
import com.wpanther.cancellationnote.processing.domain.port.out.ProcessedCancellationNoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProcessedCancellationNoteRepositoryImplTest {

    @Autowired
    private ProcessedCancellationNoteRepository repository;

    private ProcessedCancellationNote buildNote(String sourceNoteId, String noteNumber) {
        Party seller = Party.of("Seller Co", TaxIdentifier.of("1234567890", "VAT"),
                Address.of("1 St", "Bangkok", "10110", "TH"), null);
        Party buyer = Party.of("Buyer Co", TaxIdentifier.of("0987654321", "VAT"),
                Address.of("2 Rd", "CM", "50000", "TH"), null);
        LineItem item = new LineItem("Svc", 1,
                Money.of(new BigDecimal("1000.00"), "THB"), new BigDecimal("7.00"));
        return ProcessedCancellationNote.builder()
                .id(CancellationNoteId.generate())
                .sourceNoteId(sourceNoteId)
                .cancellationNoteNumber(noteNumber)
                .issueDate(LocalDate.of(2025, 1, 1))
                .cancellationDate(LocalDate.of(2025, 1, 10))
                .seller(seller).buyer(buyer)
                .items(List.of(item))
                .currency("THB")
                .cancelledInvoiceNumber("INV-REF-001")
                .originalXml("<xml/>")
                .build();
    }

    @Test
    void save_andFindById_roundTrip() {
        ProcessedCancellationNote note = buildNote("src-001", "CN-001");
        ProcessedCancellationNote saved = repository.save(note);

        Optional<ProcessedCancellationNote> found = repository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals("CN-001", found.get().cancellationNoteNumber());
        assertEquals(1, found.get().items().size());
    }

    @Test
    void findBySourceNoteId_returnsMatch() {
        repository.save(buildNote("src-002", "CN-002"));

        Optional<ProcessedCancellationNote> found = repository.findBySourceNoteId("src-002");
        assertTrue(found.isPresent());
        assertEquals("CN-002", found.get().cancellationNoteNumber());
    }

    @Test
    void findBySourceNoteId_returnsEmpty_whenNotFound() {
        Optional<ProcessedCancellationNote> found = repository.findBySourceNoteId("nonexistent");
        assertFalse(found.isPresent());
    }

    @Test
    void findByCancellationNoteNumber_returnsMatch() {
        repository.save(buildNote("src-003", "CN-003"));

        Optional<ProcessedCancellationNote> found = repository.findByCancellationNoteNumber("CN-003");
        assertTrue(found.isPresent());
        assertEquals("src-003", found.get().sourceNoteId());
    }

    @Test
    void deleteById_removesRecord() {
        ProcessedCancellationNote saved = repository.save(buildNote("src-004", "CN-004"));
        repository.deleteById(saved.id());

        Optional<ProcessedCancellationNote> found = repository.findById(saved.id());
        assertFalse(found.isPresent());
    }
}
```

- [ ] **Step 15: Run all persistence tests**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test \
     -Dtest="ProcessedCancellationNoteMapperTest,ProcessedCancellationNoteEntityTest,CancellationNoteLineItemEntityTest,CancellationNotePartyEntityTest,OutboxEventEntityTest,OutboxCleanupSchedulerTest,ProcessedCancellationNoteRepositoryImplTest" \
     2>&1 | tail -15
```

Expected: All tests pass (`Failures: 0, Errors: 0`).

- [ ] **Step 16: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && git add \
     src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ \
     src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/persistence/ \
     src/main/java/com/wpanther/cancellationnote/processing/infrastructure/config/OutboxConfig.java \
  && git commit -m "refactor: add persistence adapter with mapper, outbox cleanup scheduler, and tests"
```

---

*Tasks 5–11 (outbound messaging adapters, parsing adapter, application service, inbound adapter, domain model tests, cleanup) will be added in the next batch.*
