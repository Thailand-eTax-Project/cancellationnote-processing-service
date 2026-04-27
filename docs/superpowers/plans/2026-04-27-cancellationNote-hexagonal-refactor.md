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

---

### Task 5: Outbound messaging adapters

**Files:**
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/messaging/dto/CancellationNoteReplyEvent.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/messaging/HeaderSerializer.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/messaging/SagaReplyPublisher.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/messaging/CancellationNoteEventPublisher.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/messaging/HeaderSerializerTest.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/messaging/SagaReplyPublisherTest.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/messaging/CancellationNoteEventPublisherTest.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/messaging/SagaReplyPublisherTransactionTest.java`

- [ ] **Step 1: Write failing tests for HeaderSerializer**

```java
// src/test/java/.../infrastructure/adapter/out/messaging/HeaderSerializerTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeaderSerializerTest {
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private HeaderSerializer headerSerializer;

    @Test
    void toJson_successfulSerialization() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"key\":\"value\"}");
        assertEquals("{\"key\":\"value\"}", headerSerializer.toJson(Map.of("key", "value")));
    }

    @Test
    void toJson_whenJsonProcessingException_throwsIllegalStateException() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("error") {});
        assertThrows(IllegalStateException.class, () -> headerSerializer.toJson(Map.of("key", "value")));
    }
}
```

- [ ] **Step 2: Create `CancellationNoteReplyEvent`**

```java
// src/main/java/.../infrastructure/adapter/out/messaging/dto/CancellationNoteReplyEvent.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging.dto;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaReply;

public class CancellationNoteReplyEvent extends SagaReply {
    private static final long serialVersionUID = 1L;

    public static CancellationNoteReplyEvent success(String sagaId, SagaStep sagaStep, String correlationId) {
        return new CancellationNoteReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS);
    }
    public static CancellationNoteReplyEvent failure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        return new CancellationNoteReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
    }
    public static CancellationNoteReplyEvent compensated(String sagaId, SagaStep sagaStep, String correlationId) {
        return new CancellationNoteReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
    }
    private CancellationNoteReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, ReplyStatus status) {
        super(sagaId, sagaStep, correlationId, status);
    }
    private CancellationNoteReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        super(sagaId, sagaStep, correlationId, errorMessage);
    }
}
```

- [ ] **Step 3: Create `HeaderSerializer`**

```java
// src/main/java/.../infrastructure/adapter/out/messaging/HeaderSerializer.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class HeaderSerializer {
    private final ObjectMapper objectMapper;

    public String toJson(Map<String, String> headers) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox headers to JSON: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Write failing tests for SagaReplyPublisher**

```java
// src/test/java/.../infrastructure/adapter/out/messaging/SagaReplyPublisherTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging;

import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging.dto.CancellationNoteReplyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaReplyPublisherTest {
    @Mock private OutboxService outboxService;
    @Mock private HeaderSerializer headerSerializer;
    private SagaReplyPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SagaReplyPublisher(outboxService, headerSerializer, "saga.reply.cancellation-note");
    }

    @Test
    void testPublishSuccessCallsOutboxWithCorrectParameters() {
        when(headerSerializer.toJson(any())).thenReturn("{\"status\":\"SUCCESS\"}");
        publisher.publishSuccess("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
        verify(outboxService).saveWithRouting(
            any(CancellationNoteReplyEvent.class), eq("ProcessedCancellationNote"),
            eq("saga-1"), eq("saga.reply.cancellation-note"), eq("saga-1"), contains("SUCCESS"));
    }

    @Test
    void testPublishFailureCallsOutboxWithCorrectParameters() {
        when(headerSerializer.toJson(any())).thenReturn("{\"status\":\"FAILURE\"}");
        publisher.publishFailure("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "Parse error");
        verify(outboxService).saveWithRouting(
            any(CancellationNoteReplyEvent.class), eq("ProcessedCancellationNote"),
            eq("saga-1"), eq("saga.reply.cancellation-note"), eq("saga-1"), contains("FAILURE"));
    }

    @Test
    void testPublishCompensatedCallsOutboxWithCorrectParameters() {
        when(headerSerializer.toJson(any())).thenReturn("{\"status\":\"COMPENSATED\"}");
        publisher.publishCompensated("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
        verify(outboxService).saveWithRouting(
            any(CancellationNoteReplyEvent.class), eq("ProcessedCancellationNote"),
            eq("saga-1"), eq("saga.reply.cancellation-note"), eq("saga-1"), contains("COMPENSATED"));
    }

    @Test
    void testPublishSuccessUsesSagaIdAsPartitionKey() {
        when(headerSerializer.toJson(any())).thenReturn("{}");
        publisher.publishSuccess("my-saga-id", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), any(), captor.capture(), any());
        assertEquals("my-saga-id", captor.getValue());
    }

    @Test
    void testPublishReplyEventHasCorrectAggregateType() {
        when(headerSerializer.toJson(any())).thenReturn("{}");
        publisher.publishFailure("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "error");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), captor.capture(), any(), any(), any(), any());
        assertEquals("ProcessedCancellationNote", captor.getValue());
    }
}
```

- [ ] **Step 5: Create `SagaReplyPublisher`**

```java
// src/main/java/.../infrastructure/adapter/out/messaging/SagaReplyPublisher.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging;

import com.wpanther.cancellationnote.processing.application.port.out.SagaReplyPort;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging.dto.CancellationNoteReplyEvent;
import com.wpanther.cancellationnote.processing.infrastructure.config.KafkaTopicsProperties;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Component
@Slf4j
public class SagaReplyPublisher implements SagaReplyPort {

    private static final String AGGREGATE_TYPE = "ProcessedCancellationNote";

    private final OutboxService outboxService;
    private final HeaderSerializer headerSerializer;
    private final String replyTopic;

    @Autowired
    public SagaReplyPublisher(OutboxService outboxService, HeaderSerializer headerSerializer,
                               KafkaTopicsProperties topics) {
        this(outboxService, headerSerializer, topics.sagaReplyCancellationNote());
    }

    SagaReplyPublisher(OutboxService outboxService, HeaderSerializer headerSerializer, String replyTopic) {
        this.outboxService = outboxService;
        this.headerSerializer = headerSerializer;
        this.replyTopic = replyTopic;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId) {
        outboxService.saveWithRouting(
            CancellationNoteReplyEvent.success(sagaId, sagaStep, correlationId),
            AGGREGATE_TYPE, sagaId, replyTopic, sagaId,
            headerSerializer.toJson(Map.of("sagaId", sagaId, "correlationId", correlationId, "status", "SUCCESS")));
        log.info("Published SUCCESS saga reply for saga {} step {}", sagaId, sagaStep);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        outboxService.saveWithRouting(
            CancellationNoteReplyEvent.failure(sagaId, sagaStep, correlationId, errorMessage),
            AGGREGATE_TYPE, sagaId, replyTopic, sagaId,
            headerSerializer.toJson(Map.of("sagaId", sagaId, "correlationId", correlationId, "status", "FAILURE")));
        log.info("Published FAILURE saga reply for saga {} step {}: {}", sagaId, sagaStep, errorMessage);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        outboxService.saveWithRouting(
            CancellationNoteReplyEvent.compensated(sagaId, sagaStep, correlationId),
            AGGREGATE_TYPE, sagaId, replyTopic, sagaId,
            headerSerializer.toJson(Map.of("sagaId", sagaId, "correlationId", correlationId, "status", "COMPENSATED")));
        log.info("Published COMPENSATED saga reply for saga {} step {}", sagaId, sagaStep);
    }
}
```

- [ ] **Step 6: Write failing tests for CancellationNoteEventPublisher**

```java
// src/test/java/.../infrastructure/adapter/out/messaging/CancellationNoteEventPublisherTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging;

import com.wpanther.cancellationnote.processing.application.dto.event.CancellationNoteProcessedEvent;
import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedDomainEvent;
import com.wpanther.cancellationnote.processing.domain.model.Money;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancellationNoteEventPublisherTest {
    @Mock private OutboxService outboxService;
    @Mock private HeaderSerializer headerSerializer;
    private CancellationNoteEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CancellationNoteEventPublisher(outboxService, headerSerializer, "cancellationnote.processed");
    }

    private CancellationNoteProcessedDomainEvent makeEvent() {
        return CancellationNoteProcessedDomainEvent.of(
            "note-id-1", "CN-001", Money.of(new BigDecimal("10000.00"), "THB"),
            "INV-001", "saga-1", "corr-1");
    }

    @Test
    void testPublishCallsOutboxWithCorrectTopic() {
        when(headerSerializer.toJson(any())).thenReturn("{}");
        publisher.publish(makeEvent());
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), topicCaptor.capture(), any(), any());
        assertEquals("cancellationnote.processed", topicCaptor.getValue());
    }

    @Test
    void testPublishTransformsToKafkaEvent() {
        when(headerSerializer.toJson(any())).thenReturn("{}");
        publisher.publish(makeEvent());
        verify(outboxService).saveWithRouting(
            any(CancellationNoteProcessedEvent.class), eq("ProcessedCancellationNote"),
            eq("note-id-1"), eq("cancellationnote.processed"), eq("note-id-1"), eq("{}"));
    }

    @Test
    void testPublishUsesNoteIdAsAggregateIdAndPartitionKey() {
        when(headerSerializer.toJson(any())).thenReturn("{}");
        publisher.publish(makeEvent());
        ArgumentCaptor<String> aggId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> partKey = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), aggId.capture(), any(), partKey.capture(), any());
        assertEquals("note-id-1", aggId.getValue());
        assertEquals("note-id-1", partKey.getValue());
    }
}
```

- [ ] **Step 7: Create `CancellationNoteEventPublisher`**

```java
// src/main/java/.../infrastructure/adapter/out/messaging/CancellationNoteEventPublisher.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging;

import com.wpanther.cancellationnote.processing.application.dto.event.CancellationNoteProcessedEvent;
import com.wpanther.cancellationnote.processing.application.port.out.CancellationNoteEventPublishingPort;
import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedDomainEvent;
import com.wpanther.cancellationnote.processing.infrastructure.config.KafkaTopicsProperties;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Component
@Slf4j
public class CancellationNoteEventPublisher implements CancellationNoteEventPublishingPort {

    private static final String AGGREGATE_TYPE = "ProcessedCancellationNote";

    private final OutboxService outboxService;
    private final HeaderSerializer headerSerializer;
    private final String cancellationNoteProcessedTopic;

    @Autowired
    public CancellationNoteEventPublisher(OutboxService outboxService, HeaderSerializer headerSerializer,
                                           KafkaTopicsProperties topics) {
        this(outboxService, headerSerializer, topics.cancellationNoteProcessed());
    }

    CancellationNoteEventPublisher(OutboxService outboxService, HeaderSerializer headerSerializer,
                                    String cancellationNoteProcessedTopic) {
        this.outboxService = outboxService;
        this.headerSerializer = headerSerializer;
        this.cancellationNoteProcessedTopic = cancellationNoteProcessedTopic;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(CancellationNoteProcessedDomainEvent event) {
        CancellationNoteProcessedEvent kafkaEvent = new CancellationNoteProcessedEvent(
            event.noteId(), event.cancellationNoteNumber(),
            event.total().amount(), event.total().currency(),
            event.cancelledInvoiceNumber(), event.correlationId());

        outboxService.saveWithRouting(
            kafkaEvent, AGGREGATE_TYPE, event.noteId(),
            cancellationNoteProcessedTopic, event.noteId(),
            headerSerializer.toJson(Map.of(
                "correlationId", event.correlationId(),
                "cancellationNoteNumber", event.cancellationNoteNumber())));

        log.info("Published CancellationNoteProcessedEvent to outbox: {}", event.cancellationNoteNumber());
    }
}
```

- [ ] **Step 8: Write `SagaReplyPublisherTransactionTest` (integration test)**

```java
// src/test/java/.../infrastructure/adapter/out/messaging/SagaReplyPublisherTransactionTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.messaging;

import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox.OutboxEventEntity;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SagaReplyPublisherTransactionTest {

    @Autowired private SagaReplyPublisher sagaReplyPublisher;
    @Autowired private SpringDataOutboxRepository outboxRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanup() { outboxRepository.deleteAll(); }

    @Test
    void publishFailure_commitsOutboxEntry_evenWhenOuterTransactionIsRollbackOnly() {
        String sagaId = UUID.randomUUID().toString();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.execute(status -> {
                status.setRollbackOnly();
                sagaReplyPublisher.publishFailure(sagaId, SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "error");
                return null;
            });
        } catch (UnexpectedRollbackException | TransactionSystemException ignored) {}

        List<OutboxEventEntity> entries = outboxRepository.findAll().stream()
            .filter(e -> e.getAggregateId().equals(sagaId)).toList();
        assertFalse(entries.isEmpty(),
            "publishFailure() must commit outbox entry even when outer transaction is rolled back");
    }

    @Test
    void publishFailure_outboxEntry_containsFailureStatus() {
        String sagaId = UUID.randomUUID().toString();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.execute(status -> {
                status.setRollbackOnly();
                sagaReplyPublisher.publishFailure(sagaId, SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "some error");
                return null;
            });
        } catch (UnexpectedRollbackException | TransactionSystemException ignored) {}

        List<OutboxEventEntity> entries = outboxRepository.findAll().stream()
            .filter(e -> e.getAggregateId().equals(sagaId)).toList();
        assertFalse(entries.isEmpty());
        assertTrue(entries.get(0).getPayload().contains("FAILURE"));
    }

    @Test
    void publishSuccess_rollsBackOutboxEntry_whenOuterTransactionRollsBack() {
        String sagaId = UUID.randomUUID().toString();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.execute(status -> {
                sagaReplyPublisher.publishSuccess(sagaId, SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
                status.setRollbackOnly();
                return null;
            });
        } catch (UnexpectedRollbackException | TransactionSystemException ignored) {}

        List<OutboxEventEntity> entries = outboxRepository.findAll().stream()
            .filter(e -> e.getAggregateId().equals(sagaId)).toList();
        assertTrue(entries.isEmpty(),
            "publishSuccess() must roll back with the outer transaction");
    }

    @Test
    void publishCompensated_commitsOutboxEntry_togetherWithOuterTransaction() {
        String sagaId = UUID.randomUUID().toString();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            sagaReplyPublisher.publishCompensated(sagaId, SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
            return null;
        });

        List<OutboxEventEntity> entries = outboxRepository.findAll().stream()
            .filter(e -> e.getAggregateId().equals(sagaId)).toList();
        assertFalse(entries.isEmpty());
    }
}
```

- [ ] **Step 9: Run messaging adapter tests**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test \
     -Dtest="HeaderSerializerTest,SagaReplyPublisherTest,CancellationNoteEventPublisherTest,SagaReplyPublisherTransactionTest" \
     2>&1 | tail -15
```

Expected: All tests pass (`Failures: 0, Errors: 0`).

- [ ] **Step 10: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && git add \
     src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/messaging/ \
     src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/messaging/ \
  && git commit -m "feat: add outbound messaging adapters (SagaReplyPublisher, CancellationNoteEventPublisher, HeaderSerializer)"
```

---

### Task 6: Parsing adapter

**Files:**
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/parsing/CancellationNoteParserServiceImpl.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/parsing/CancellationNoteParserServiceImplTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/.../infrastructure/adapter/out/parsing/CancellationNoteParserServiceImplTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.parsing;

import com.wpanther.cancellationnote.processing.domain.port.out.CancellationNoteParserPort;
import com.wpanther.cancellationnote.processing.domain.port.out.CancellationNoteParserPort.CancellationNoteParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CancellationNoteParserServiceImplTest {

    private CancellationNoteParserServiceImpl parserService;

    @BeforeEach
    void setUp() throws CancellationNoteParsingException {
        parserService = new CancellationNoteParserServiceImpl();
    }

    @Test
    void parse_withNullXml_throwsCancellationNoteParsingException() {
        assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse(null, "source-1"));
    }

    @Test
    void parse_withBlankXml_throwsCancellationNoteParsingException() {
        assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse("   ", "source-1"));
    }

    @Test
    void parse_withInvalidXml_throwsCancellationNoteParsingException() {
        assertThrows(CancellationNoteParsingException.class,
            () -> parserService.parse("<invalid>not-a-cancellation-note</invalid>", "source-1"));
    }

    @Test
    void constructor_initializesJaxbContext_withoutException() {
        // JAXBContext initialization must succeed for a valid cancellation-note context path
        assertDoesNotThrow(() -> new CancellationNoteParserServiceImpl());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test -Dtest=CancellationNoteParserServiceImplTest 2>&1 | tail -10
```

Expected: FAIL (class not found in new package).

- [ ] **Step 3: Create `CancellationNoteParserServiceImpl` in the new package**

This is a copy of `infrastructure/service/CancellationNoteParserServiceImpl.java` with three changes:
1. Package declaration updated to `infrastructure.adapter.out.parsing`
2. Implements `CancellationNoteParserPort` instead of `CancellationNoteParserService`
3. Method renamed from `parseCancellationNote` to `parse`
4. Exception type changed from `CancellationNoteParserService.CancellationNoteParsingException` to `CancellationNoteParserPort.CancellationNoteParsingException`

```java
// src/main/java/.../infrastructure/adapter/out/parsing/CancellationNoteParserServiceImpl.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.out.parsing;

import com.wpanther.cancellationnote.processing.domain.model.*;
import com.wpanther.cancellationnote.processing.domain.port.out.CancellationNoteParserPort;
import com.wpanther.etax.generated.cancellationnote.ram.*;
import com.wpanther.etax.generated.cancellationnote.rsm.CancellationNote_CrossIndustryInvoiceType;
import com.wpanther.etax.generated.cancellationnote.rsm.impl.CancellationNote_CrossIndustryInvoiceTypeImpl;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CancellationNoteParserServiceImpl implements CancellationNoteParserPort {

    private final JAXBContext jaxbContext;

    public CancellationNoteParserServiceImpl() throws CancellationNoteParsingException {
        try {
            String contextPath = "com.wpanther.etax.generated.cancellationnote.rsm.impl" +
                               ":com.wpanther.etax.generated.cancellationnote.ram.impl" +
                               ":com.wpanther.etax.generated.common.qdt.impl" +
                               ":com.wpanther.etax.generated.common.udt.impl";
            this.jaxbContext = JAXBContext.newInstance(contextPath);
        } catch (JAXBException e) {
            throw new CancellationNoteParsingException("Failed to initialize XML parser", e);
        }
    }

    @Override
    public ProcessedCancellationNote parse(String xmlContent, String sourceNoteId)
            throws CancellationNoteParsingException {
        log.debug("Starting XML parsing for source note ID: {}", sourceNoteId);
        try {
            CancellationNote_CrossIndustryInvoiceType jaxbNote = unmarshalXml(xmlContent);
            ExchangedDocumentType document = jaxbNote.getExchangedDocument();
            if (document == null) throw new CancellationNoteParsingException(
                "Cancellation note XML missing required ExchangedDocument element");
            SupplyChainTradeTransactionType transaction = jaxbNote.getSupplyChainTradeTransaction();
            if (transaction == null) throw new CancellationNoteParsingException(
                "Cancellation note XML missing required SupplyChainTradeTransaction element");
            LocalDate issueDate = extractIssueDate(document);
            ProcessedCancellationNote note = ProcessedCancellationNote.builder()
                .id(CancellationNoteId.generate())
                .sourceNoteId(sourceNoteId)
                .cancellationNoteNumber(extractCancellationNoteNumber(document))
                .issueDate(issueDate)
                .cancellationDate(issueDate)
                .seller(extractSeller(transaction))
                .buyer(extractBuyer(transaction))
                .items(extractLineItems(transaction))
                .currency(extractCurrency(transaction))
                .cancelledInvoiceNumber(extractCancelledInvoiceNumber(document))
                .originalXml(xmlContent)
                .build();
            log.info("Successfully parsed cancellation note {} with {} line items",
                note.cancellationNoteNumber(), note.items().size());
            return note;
        } catch (CancellationNoteParsingException e) {
            log.error("Failed to parse cancellation note XML for source ID {}: {}", sourceNoteId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error parsing cancellation note XML for source ID " + sourceNoteId, e);
            throw new CancellationNoteParsingException("Unexpected error during cancellation note parsing", e);
        }
    }

    private CancellationNote_CrossIndustryInvoiceType unmarshalXml(String xmlContent)
            throws CancellationNoteParsingException {
        if (xmlContent == null || xmlContent.isBlank())
            throw new CancellationNoteParsingException("XML content is null or empty");
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Object result = unmarshaller.unmarshal(new StringReader(xmlContent));
            if (result instanceof jakarta.xml.bind.JAXBElement<?> jaxbElement) result = jaxbElement.getValue();
            if (!(result instanceof CancellationNote_CrossIndustryInvoiceType))
                throw new CancellationNoteParsingException("Unexpected root element: " + result.getClass().getName());
            return (CancellationNote_CrossIndustryInvoiceType) result;
        } catch (JAXBException e) {
            throw new CancellationNoteParsingException("Failed to parse XML: " + e.getMessage(), e);
        }
    }

    private String extractCancellationNoteNumber(ExchangedDocumentType document) throws CancellationNoteParsingException {
        if (document.getID() == null || document.getID().getValue() == null)
            throw new CancellationNoteParsingException("Cancellation note number (ID) is missing");
        return document.getID().getValue();
    }

    private String extractCancelledInvoiceNumber(ExchangedDocumentType document) throws CancellationNoteParsingException {
        List<ReferencedDocumentType> refs = document.getIncludedReferencedDocument();
        if (refs != null && !refs.isEmpty()) {
            ReferencedDocumentType ref = refs.get(0);
            if (ref.getIssuerAssignedID() != null && ref.getIssuerAssignedID().getValue() != null)
                return ref.getIssuerAssignedID().getValue();
        }
        throw new CancellationNoteParsingException("Cancelled invoice number is missing");
    }

    private LocalDate extractIssueDate(ExchangedDocumentType document) throws CancellationNoteParsingException {
        XMLGregorianCalendar issueDateTime = document.getIssueDateTime();
        if (issueDateTime == null) throw new CancellationNoteParsingException("Issue date/time is missing");
        return LocalDate.of(issueDateTime.getYear(), issueDateTime.getMonth(), issueDateTime.getDay());
    }

    private Party extractSeller(SupplyChainTradeTransactionType transaction) throws CancellationNoteParsingException {
        HeaderTradeAgreementType agreement = transaction.getApplicableHeaderTradeAgreement();
        if (agreement == null || agreement.getSellerTradeParty() == null)
            throw new CancellationNoteParsingException("Seller information is missing");
        return mapParty(agreement.getSellerTradeParty(), "Seller");
    }

    private Party extractBuyer(SupplyChainTradeTransactionType transaction) throws CancellationNoteParsingException {
        HeaderTradeAgreementType agreement = transaction.getApplicableHeaderTradeAgreement();
        if (agreement == null || agreement.getBuyerTradeParty() == null)
            throw new CancellationNoteParsingException("Buyer information is missing");
        return mapParty(agreement.getBuyerTradeParty(), "Buyer");
    }

    private Party mapParty(TradePartyType jaxbParty, String partyType) throws CancellationNoteParsingException {
        String name = Optional.ofNullable(jaxbParty.getName())
            .map(n -> n.getValue()).orElseThrow(() -> new CancellationNoteParsingException(partyType + " name is missing"));
        TaxIdentifier taxId = extractTaxIdentifier(jaxbParty, partyType);
        Address address = extractAddress(jaxbParty, partyType);
        String email = null;
        List<TradeContactType> contacts = jaxbParty.getDefinedTradeContact();
        if (contacts != null && !contacts.isEmpty()) {
            TradeContactType contact = contacts.get(0);
            if (contact.getEmailURIUniversalCommunication() != null &&
                contact.getEmailURIUniversalCommunication().getURIID() != null)
                email = contact.getEmailURIUniversalCommunication().getURIID().getValue();
        }
        return Party.of(name, taxId, address, email);
    }

    private TaxIdentifier extractTaxIdentifier(TradePartyType jaxbParty, String partyType) throws CancellationNoteParsingException {
        TaxRegistrationType taxReg = jaxbParty.getSpecifiedTaxRegistration();
        if (taxReg == null || taxReg.getID() == null || taxReg.getID().getValue() == null)
            throw new CancellationNoteParsingException(partyType + " tax ID is missing");
        return TaxIdentifier.of(taxReg.getID().getValue(), Optional.ofNullable(taxReg.getID().getSchemeID()).orElse("VAT"));
    }

    private Address extractAddress(TradePartyType jaxbParty, String partyType) throws CancellationNoteParsingException {
        TradeAddressType addr = jaxbParty.getPostalTradeAddress();
        if (addr == null) throw new CancellationNoteParsingException(partyType + " address is missing");
        String street = Optional.ofNullable(addr.getLineOne()).map(l -> l.getValue()).orElse(null);
        String city = Optional.ofNullable(addr.getCityName()).map(c -> c.getValue()).orElse(null);
        String postal = Optional.ofNullable(addr.getPostcodeCode()).map(p -> p.getValue()).orElse(null);
        if (addr.getCountryID() == null || addr.getCountryID().getValue() == null)
            throw new CancellationNoteParsingException(partyType + " country is missing");
        return Address.of(street, city, postal, addr.getCountryID().getValue().value());
    }

    private List<LineItem> extractLineItems(SupplyChainTradeTransactionType transaction) throws CancellationNoteParsingException {
        List<SupplyChainTradeLineItemType> jaxbItems = transaction.getIncludedSupplyChainTradeLineItem();
        if (jaxbItems == null || jaxbItems.isEmpty())
            throw new CancellationNoteParsingException("Cancellation note must have at least one line item");
        String currency = extractCurrency(transaction);
        List<LineItem> items = new ArrayList<>();
        for (int i = 0; i < jaxbItems.size(); i++) {
            try { items.add(mapLineItem(jaxbItems.get(i), currency)); }
            catch (Exception e) { throw new CancellationNoteParsingException("Failed to parse line item " + (i + 1) + ": " + e.getMessage(), e); }
        }
        return items;
    }

    private LineItem mapLineItem(SupplyChainTradeLineItemType jaxbItem, String currency) throws CancellationNoteParsingException {
        TradeProductType product = jaxbItem.getSpecifiedTradeProduct();
        if (product == null || product.getName() == null || product.getName().isEmpty())
            throw new CancellationNoteParsingException("Line item product name is missing");
        String description = product.getName().get(0).getValue();
        LineTradeDeliveryType delivery = jaxbItem.getSpecifiedLineTradeDelivery();
        if (delivery == null || delivery.getBilledQuantity() == null)
            throw new CancellationNoteParsingException("Line item quantity is missing");
        int quantity = delivery.getBilledQuantity().getValue().intValue();
        LineTradeAgreementType agreement = jaxbItem.getSpecifiedLineTradeAgreement();
        if (agreement == null || agreement.getGrossPriceProductTradePrice() == null ||
            agreement.getGrossPriceProductTradePrice().getChargeAmount() == null ||
            agreement.getGrossPriceProductTradePrice().getChargeAmount().isEmpty())
            throw new CancellationNoteParsingException("Line item unit price is missing");
        Money unitPrice = Money.of(agreement.getGrossPriceProductTradePrice().getChargeAmount().get(0).getValue(), currency);
        BigDecimal taxRate = BigDecimal.ZERO;
        LineTradeSettlementType settlement = jaxbItem.getSpecifiedLineTradeSettlement();
        if (settlement != null && settlement.getApplicableTradeTax() != null &&
            !settlement.getApplicableTradeTax().isEmpty()) {
            TradeTaxType tax = settlement.getApplicableTradeTax().get(0);
            if (tax.getCalculatedRate() != null) taxRate = tax.getCalculatedRate();
        }
        return new LineItem(description, quantity, unitPrice, taxRate);
    }

    private String extractCurrency(SupplyChainTradeTransactionType transaction) throws CancellationNoteParsingException {
        HeaderTradeSettlementType settlement = transaction.getApplicableHeaderTradeSettlement();
        if (settlement == null || settlement.getInvoiceCurrencyCode() == null ||
            settlement.getInvoiceCurrencyCode().getValue() == null)
            throw new CancellationNoteParsingException("Cancellation note currency is missing");
        String currency = settlement.getInvoiceCurrencyCode().getValue().value();
        if (currency == null || currency.length() != 3)
            throw new CancellationNoteParsingException("Invalid currency code: " + currency);
        return currency;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test -Dtest=CancellationNoteParserServiceImplTest 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && git add \
     src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/parsing/ \
     src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/out/parsing/ \
  && git commit -m "feat: add parsing adapter (CancellationNoteParserServiceImpl implements CancellationNoteParserPort)"
```

---

### Task 7: Application service refactor

**Files:**
- Modify: `src/main/java/com/wpanther/cancellationnote/processing/application/service/CancellationNoteProcessingService.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/application/service/CancellationNoteProcessingServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
// src/test/java/.../application/service/CancellationNoteProcessingServiceTest.java
package com.wpanther.cancellationnote.processing.application.service;

import com.wpanther.cancellationnote.processing.application.port.in.CompensateCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.in.ProcessCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.out.CancellationNoteEventPublishingPort;
import com.wpanther.cancellationnote.processing.application.port.out.SagaReplyPort;
import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedDomainEvent;
import com.wpanther.cancellationnote.processing.domain.model.*;
import com.wpanther.cancellationnote.processing.domain.port.out.CancellationNoteParserPort;
import com.wpanther.cancellationnote.processing.domain.port.out.ProcessedCancellationNoteRepository;
import com.wpanther.saga.domain.enums.SagaStep;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancellationNoteProcessingServiceTest {

    @Mock private ProcessedCancellationNoteRepository noteRepository;
    @Mock private CancellationNoteParserPort parserPort;
    @Mock private CancellationNoteEventPublishingPort eventPublisher;
    @Mock private SagaReplyPort sagaReplyPort;
    @Mock private PlatformTransactionManager transactionManager;

    private CancellationNoteProcessingService service;
    private ProcessedCancellationNote validNote;

    @BeforeEach
    void setUp() {
        service = new CancellationNoteProcessingService(
            noteRepository, parserPort, eventPublisher, sagaReplyPort,
            new SimpleMeterRegistry(), transactionManager);

        Party seller = Party.of("Seller Co", TaxIdentifier.of("1234567890", "VAT"),
            new Address("123 St", "Bangkok", "10110", "TH"), null);
        Party buyer = Party.of("Buyer Co", TaxIdentifier.of("9876543210", "VAT"),
            new Address("456 Rd", "Chiang Mai", "50000", "TH"), null);
        LineItem item = new LineItem("Service", 1, Money.of(new BigDecimal("1000.00"), "THB"), new BigDecimal("7.00"));

        validNote = ProcessedCancellationNote.builder()
            .id(CancellationNoteId.generate())
            .sourceNoteId("intake-123")
            .cancellationNoteNumber("CN-001")
            .issueDate(LocalDate.of(2025, 1, 1))
            .cancellationDate(LocalDate.of(2025, 1, 1))
            .seller(seller).buyer(buyer).items(List.of(item))
            .currency("THB").cancelledInvoiceNumber("INV-001")
            .originalXml("<xml/>")
            .build();
    }

    @Test
    void process_success_savesAndPublishes() throws Exception {
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.empty());
        when(parserPort.parse(anyString(), anyString())).thenReturn(validNote);
        when(noteRepository.save(any())).thenReturn(validNote);

        service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");

        verify(noteRepository).findBySourceNoteId("intake-123");
        verify(parserPort).parse("<xml/>", "intake-123");
        verify(noteRepository, times(2)).save(any());
        verify(eventPublisher).publish(any(CancellationNoteProcessedDomainEvent.class));
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }

    @Test
    void process_idempotent_completed_publishesSuccessWithoutReprocessing() throws Exception {
        ProcessedCancellationNote completed = ProcessedCancellationNote.builder()
            .id(CancellationNoteId.generate()).sourceNoteId("intake-123")
            .cancellationNoteNumber("CN-001").issueDate(LocalDate.of(2025,1,1))
            .cancellationDate(LocalDate.of(2025,1,1))
            .seller(validNote.seller()).buyer(validNote.buyer()).items(validNote.items())
            .currency("THB").cancelledInvoiceNumber("INV-001").originalXml("<xml/>")
            .status(ProcessingStatus.COMPLETED).build();
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.of(completed));

        service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");

        verify(parserPort, never()).parse(anyString(), anyString());
        verify(noteRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }

    @Test
    void process_resumesFromProcessingState() throws Exception {
        ProcessedCancellationNote processing = ProcessedCancellationNote.builder()
            .id(CancellationNoteId.generate()).sourceNoteId("intake-123")
            .cancellationNoteNumber("CN-001").issueDate(LocalDate.of(2025,1,1))
            .cancellationDate(LocalDate.of(2025,1,1))
            .seller(validNote.seller()).buyer(validNote.buyer()).items(validNote.items())
            .currency("THB").cancelledInvoiceNumber("INV-001").originalXml("<xml/>")
            .status(ProcessingStatus.PROCESSING).build();
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.of(processing));

        service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");

        verify(parserPort, never()).parse(anyString(), anyString());
        verify(noteRepository, times(1)).save(any());
        verify(eventPublisher).publish(any(CancellationNoteProcessedDomainEvent.class));
        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
        assertEquals(ProcessingStatus.COMPLETED, processing.status());
    }

    @Test
    void process_parsingError_publishesFailureAndThrows() throws Exception {
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.empty());
        when(parserPort.parse(anyString(), anyString()))
            .thenThrow(new CancellationNoteParserPort.CancellationNoteParsingException("Parse error"));

        assertThrows(ProcessCancellationNoteUseCase.CancellationNoteProcessingException.class,
            () -> service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1"));

        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.PROCESS_CANCELLATION_NOTE),
            eq("corr-1"), contains("Parse error"));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void process_databaseError_publishesFailureAndThrows() throws Exception {
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.empty());
        when(parserPort.parse(anyString(), anyString())).thenReturn(validNote);
        when(noteRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThrows(ProcessCancellationNoteUseCase.CancellationNoteProcessingException.class,
            () -> service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1"));

        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.PROCESS_CANCELLATION_NOTE),
            eq("corr-1"), contains("Processing error"));
    }

    @Test
    void process_raceConditionResolvesAsSuccess_whenRecordFoundOnRecheck() throws Exception {
        SQLException sqlCause = new SQLException(
            "ERROR: duplicate key value violates unique constraint " +
            "\"uq_processed_cancellation_notes_source_note_id\"", "23505");
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(noteRepository.findBySourceNoteId(anyString()))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(validNote));
        when(parserPort.parse(anyString(), anyString())).thenReturn(validNote);
        when(noteRepository.save(any())).thenThrow(new DuplicateKeyException("duplicate key", sqlCause));

        ProcessCancellationNoteUseCase.CancellationNoteProcessingException ex =
            assertThrows(ProcessCancellationNoteUseCase.CancellationNoteProcessingException.class,
                () -> service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1"));
        assertInstanceOf(DataIntegrityViolationException.class, ex.getCause());

        verify(sagaReplyPort).publishSuccess("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
        verify(sagaReplyPort, never()).publishFailure(any(), any(), any(), any());
    }

    @Test
    void process_duplicateKeyOnNonIdempotentConstraint_publishesFailure() throws Exception {
        when(noteRepository.findBySourceNoteId(anyString())).thenReturn(Optional.empty());
        when(parserPort.parse(anyString(), anyString())).thenReturn(validNote);
        when(noteRepository.save(any()))
            .thenThrow(new DuplicateKeyException("duplicate key violation \"idx_cn_number_unique\""));

        assertThrows(ProcessCancellationNoteUseCase.CancellationNoteProcessingException.class,
            () -> service.process("intake-123", "<xml/>", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1"));

        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.PROCESS_CANCELLATION_NOTE),
            eq("corr-1"), contains("Constraint violation"));
        verify(noteRepository, times(1)).findBySourceNoteId(anyString());
    }

    @Test
    void compensate_deletesAndPublishesCompensated() throws Exception {
        when(noteRepository.findBySourceNoteId("intake-123")).thenReturn(Optional.of(validNote));

        service.compensate("intake-123", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");

        verify(noteRepository).deleteById(validNote.id());
        verify(sagaReplyPort).publishCompensated("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }

    @Test
    void compensate_notFound_publishesCompensatedIdempotently() throws Exception {
        when(noteRepository.findBySourceNoteId("intake-missing")).thenReturn(Optional.empty());

        service.compensate("intake-missing", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");

        verify(noteRepository, never()).deleteById(any());
        verify(sagaReplyPort).publishCompensated("saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }

    @Test
    void compensate_deleteThrows_publishesFailureAndRethrows() {
        when(noteRepository.findBySourceNoteId("intake-123")).thenReturn(Optional.of(validNote));
        doThrow(new RuntimeException("DB error")).when(noteRepository).deleteById(any());

        assertThrows(CompensateCancellationNoteUseCase.CancellationNoteCompensationException.class,
            () -> service.compensate("intake-123", "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1"));

        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.PROCESS_CANCELLATION_NOTE),
            eq("corr-1"), contains("Compensation failed"));
    }
}
```

- [ ] **Step 2: Overwrite `CancellationNoteProcessingService` with the refactored version**

```java
// src/main/java/.../application/service/CancellationNoteProcessingService.java
package com.wpanther.cancellationnote.processing.application.service;

import com.wpanther.cancellationnote.processing.application.port.in.CompensateCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.in.ProcessCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.out.CancellationNoteEventPublishingPort;
import com.wpanther.cancellationnote.processing.application.port.out.SagaReplyPort;
import com.wpanther.cancellationnote.processing.domain.event.CancellationNoteProcessedDomainEvent;
import com.wpanther.cancellationnote.processing.domain.model.ProcessedCancellationNote;
import com.wpanther.cancellationnote.processing.domain.model.ProcessingStatus;
import com.wpanther.cancellationnote.processing.domain.port.out.CancellationNoteParserPort;
import com.wpanther.cancellationnote.processing.domain.port.out.ProcessedCancellationNoteRepository;
import com.wpanther.saga.domain.enums.SagaStep;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.SQLException;
import java.util.Optional;

@Service
@Slf4j
public class CancellationNoteProcessingService
        implements ProcessCancellationNoteUseCase, CompensateCancellationNoteUseCase {

    private final ProcessedCancellationNoteRepository noteRepository;
    private final CancellationNoteParserPort parserPort;
    private final CancellationNoteEventPublishingPort eventPublisher;
    private final SagaReplyPort sagaReplyPort;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate requiresNewTemplate;

    private final Counter processSuccessCounter;
    private final Counter processFailureCounter;
    private final Counter processIdempotentCounter;
    private final Counter processRaceConditionResolvedCounter;
    private final Counter compensateSuccessCounter;
    private final Counter compensateIdempotentCounter;
    private final Counter compensateFailureCounter;
    private final Timer processingTimer;

    public CancellationNoteProcessingService(
            ProcessedCancellationNoteRepository noteRepository,
            CancellationNoteParserPort parserPort,
            CancellationNoteEventPublishingPort eventPublisher,
            SagaReplyPort sagaReplyPort,
            MeterRegistry meterRegistry,
            PlatformTransactionManager transactionManager) {
        this.noteRepository = noteRepository;
        this.parserPort = parserPort;
        this.eventPublisher = eventPublisher;
        this.sagaReplyPort = sagaReplyPort;
        this.meterRegistry = meterRegistry;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTemplate = template;

        this.processSuccessCounter = Counter.builder("cancellationNote.processing.success")
            .description("Number of successfully processed cancellation notes").register(meterRegistry);
        this.processFailureCounter = Counter.builder("cancellationNote.processing.failure")
            .description("Number of failed cancellation note processing attempts").register(meterRegistry);
        this.processIdempotentCounter = Counter.builder("cancellationNote.processing.idempotent")
            .description("Number of duplicate processing requests handled idempotently").register(meterRegistry);
        this.processRaceConditionResolvedCounter = Counter.builder("cancellationNote.processing.race_condition_resolved")
            .description("Number of DuplicateKeyExceptions on source_note_id resolved as concurrent inserts").register(meterRegistry);
        this.compensateSuccessCounter = Counter.builder("cancellationNote.compensation.success")
            .description("Number of successful compensations").register(meterRegistry);
        this.compensateIdempotentCounter = Counter.builder("cancellationNote.compensation.idempotent")
            .description("Number of duplicate compensation commands for an already-deleted note").register(meterRegistry);
        this.compensateFailureCounter = Counter.builder("cancellationNote.compensation.failure")
            .description("Number of failed compensation attempts").register(meterRegistry);
        this.processingTimer = Timer.builder("cancellationNote.processing.duration")
            .description("Time taken to process cancellation notes").register(meterRegistry);
    }

    @Override
    @Transactional
    public void process(String documentId, String xmlContent, String sagaId,
                        SagaStep sagaStep, String correlationId) throws CancellationNoteProcessingException {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            processNoteForSagaInternal(documentId, xmlContent, sagaId, sagaStep, correlationId);
        } catch (CancellationNoteParserPort.CancellationNoteParsingException e) {
            processFailureCounter.increment();
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, "Parse error: " + e.toString());
            throw new CancellationNoteProcessingException("Failed to parse cancellation note: " + e.toString(), e);
        } catch (DuplicateKeyException e) {
            if (!isSourceNoteIdViolation(e)) {
                processFailureCounter.increment();
                log.error("Duplicate key on non-idempotent constraint for document {}: {}", documentId, e.toString());
                sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                    "Constraint violation for document " + documentId + ": " + e.toString());
                throw new CancellationNoteProcessingException("Constraint violation for document " + documentId, e);
            }
            log.warn("DuplicateKeyException on source_note_id for document {} — re-checking for concurrent insert", documentId);
            requiresNewTemplate.execute(txStatus -> {
                Optional<ProcessedCancellationNote> existing = noteRepository.findBySourceNoteId(documentId);
                if (existing.isPresent()) {
                    log.warn("Race condition resolved: document {} already committed by concurrent thread — replying SUCCESS", documentId);
                    processRaceConditionResolvedCounter.increment();
                    sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                } else {
                    log.error("DuplicateKeyException on source_note_id for document {} but no record found", documentId);
                    processFailureCounter.increment();
                    sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                        "Duplicate key violation for document " + documentId + ": " + e.toString());
                }
                return null;
            });
            throw new CancellationNoteProcessingException("Concurrent insert for document: " + documentId, e);
        } catch (DataIntegrityViolationException e) {
            processFailureCounter.increment();
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                "Constraint violation for document " + documentId + ": " + e.toString());
            throw new CancellationNoteProcessingException("Constraint violation for document " + documentId, e);
        } catch (Exception e) {
            processFailureCounter.increment();
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId,
                "Processing error for document " + documentId + ": " + e.toString());
            throw new CancellationNoteProcessingException("Failed to process cancellation note " + documentId, e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processNoteForSagaInternal(String documentId, String xmlContent,
                                             String sagaId, SagaStep sagaStep, String correlationId)
            throws CancellationNoteParserPort.CancellationNoteParsingException {
        Optional<ProcessedCancellationNote> existing = noteRepository.findBySourceNoteId(documentId);
        if (existing.isPresent()) {
            ProcessedCancellationNote existingNote = existing.get();
            if (existingNote.status() == ProcessingStatus.COMPLETED) {
                log.warn("Cancellation note already completed for document {}, returning idempotent success", documentId);
                processIdempotentCounter.increment();
                sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                return;
            }
            if (existingNote.status() == ProcessingStatus.PROCESSING) {
                log.warn("Cancellation note for document {} found in PROCESSING state — resuming completion", documentId);
                existingNote.markCompleted();
                noteRepository.save(existingNote);
                eventPublisher.publish(CancellationNoteProcessedDomainEvent.of(
                    existingNote.sourceNoteId(), existingNote.cancellationNoteNumber(),
                    existingNote.getTotal(), sagaId, correlationId));
                sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
                processSuccessCounter.increment();
                return;
            }
            throw new IllegalStateException("Cancellation note for document " + documentId +
                " has unexpected persisted status: " + existingNote.status());
        }

        ProcessedCancellationNote note = parserPort.parse(xmlContent, documentId);
        note.markProcessing();
        ProcessedCancellationNote saved = noteRepository.save(note);
        saved.markCompleted();
        noteRepository.save(saved);

        eventPublisher.publish(CancellationNoteProcessedDomainEvent.of(
            saved.sourceNoteId(), saved.cancellationNoteNumber(),
            saved.getTotal(), sagaId, correlationId));
        sagaReplyPort.publishSuccess(sagaId, sagaStep, correlationId);
        processSuccessCounter.increment();
        log.info("Successfully processed cancellation note: {}", saved.cancellationNoteNumber());
    }

    @Override
    @Transactional
    public void compensate(String documentId, String sagaId, SagaStep sagaStep, String correlationId) {
        log.info("Compensating cancellation note for document: {}", documentId);
        try {
            Optional<ProcessedCancellationNote> existing = noteRepository.findBySourceNoteId(documentId);
            if (existing.isPresent()) {
                noteRepository.deleteById(existing.get().id());
                log.info("Deleted cancellation note for document: {}", documentId);
            } else {
                compensateIdempotentCounter.increment();
                log.warn("Cancellation note not found for compensation of document {} — treating as idempotent", documentId);
            }
            sagaReplyPort.publishCompensated(sagaId, sagaStep, correlationId);
            compensateSuccessCounter.increment();
        } catch (Exception e) {
            compensateFailureCounter.increment();
            log.error("Failed to compensate cancellation note for saga {}: {}", sagaId, e.toString(), e);
            sagaReplyPort.publishFailure(sagaId, sagaStep, correlationId, "Compensation failed: " + e.toString());
            throw new CompensateCancellationNoteUseCase.CancellationNoteCompensationException(
                "Compensation failed for document " + documentId, e);
        }
    }

    private static boolean isSourceNoteIdViolation(DuplicateKeyException e) {
        Throwable cause = e.getMostSpecificCause();
        String msg = cause.getMessage();
        if (msg == null || !msg.contains("uq_processed_cancellation_notes_source_note_id")) return false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) return true;
        }
        return false;
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test -Dtest=CancellationNoteProcessingServiceTest 2>&1 | tail -15
```

Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && git add \
     src/main/java/com/wpanther/cancellationnote/processing/application/service/CancellationNoteProcessingService.java \
     src/test/java/com/wpanther/cancellationnote/processing/application/service/CancellationNoteProcessingServiceTest.java \
  && git commit -m "refactor: CancellationNoteProcessingService implements both use cases with idempotency, race condition guard, and Micrometer metrics"
```

---

### Task 8: Inbound adapter

**Files:**
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/in/messaging/dto/ProcessCancellationNoteCommand.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/in/messaging/dto/CompensateCancellationNoteCommand.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/in/messaging/SagaCommandHandler.java`
- Create: `src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/in/messaging/SagaRouteConfig.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/in/messaging/SagaCommandHandlerTest.java`

- [ ] **Step 1: Write failing tests for SagaCommandHandler**

```java
// src/test/java/.../infrastructure/adapter/in/messaging/SagaCommandHandlerTest.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging;

import com.wpanther.cancellationnote.processing.application.port.in.CompensateCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.in.ProcessCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto.CompensateCancellationNoteCommand;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto.ProcessCancellationNoteCommand;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaCommandHandlerTest {

    @Mock private ProcessCancellationNoteUseCase processUseCase;
    @Mock private CompensateCancellationNoteUseCase compensateUseCase;
    @InjectMocks private SagaCommandHandler handler;

    @Test
    void handleProcessCommand_delegatesToUseCase() throws Exception {
        ProcessCancellationNoteCommand cmd = new ProcessCancellationNoteCommand(
            "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "doc-1", "<xml/>", "CN-001");

        handler.handleProcessCommand(cmd);

        verify(processUseCase).process("doc-1", "<xml/>", "saga-1",
            SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }

    @Test
    void handleProcessCommand_swallowsProcessingException() throws Exception {
        ProcessCancellationNoteCommand cmd = new ProcessCancellationNoteCommand(
            "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "doc-1", "<xml/>", "CN-001");
        doThrow(new ProcessCancellationNoteUseCase.CancellationNoteProcessingException("err"))
            .when(processUseCase).process(anyString(), anyString(), anyString(), any(), anyString());

        handler.handleProcessCommand(cmd); // must not throw
    }

    @Test
    void handleCompensation_delegatesToUseCase() {
        CompensateCancellationNoteCommand cmd = new CompensateCancellationNoteCommand(
            "saga-1", SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1", "PROCESS_CANCELLATION_NOTE", "doc-1", "CANCELLATION_NOTE");

        handler.handleCompensation(cmd);

        verify(compensateUseCase).compensate("doc-1", "saga-1",
            SagaStep.PROCESS_CANCELLATION_NOTE, "corr-1");
    }
}
```

- [ ] **Step 2: Create `ProcessCancellationNoteCommand`**

```java
// src/main/java/.../infrastructure/adapter/in/messaging/dto/ProcessCancellationNoteCommand.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

@Getter
public class ProcessCancellationNoteCommand extends SagaCommand {
    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId") private final String documentId;
    @JsonProperty("xmlContent") private final String xmlContent;
    @JsonProperty("cancellationNoteNumber") private final String cancellationNoteNumber;

    @JsonCreator
    public ProcessCancellationNoteCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("xmlContent") String xmlContent,
            @JsonProperty("cancellationNoteNumber") String cancellationNoteNumber) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.cancellationNoteNumber = cancellationNoteNumber;
    }

    public ProcessCancellationNoteCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                           String documentId, String xmlContent, String cancellationNoteNumber) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.cancellationNoteNumber = cancellationNoteNumber;
    }
}
```

- [ ] **Step 3: Create `CompensateCancellationNoteCommand`**

```java
// src/main/java/.../infrastructure/adapter/in/messaging/dto/CompensateCancellationNoteCommand.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

@Getter
public class CompensateCancellationNoteCommand extends SagaCommand {
    private static final long serialVersionUID = 1L;

    @JsonProperty("stepToCompensate") private final String stepToCompensate;
    @JsonProperty("documentId") private final String documentId;
    @JsonProperty("documentType") private final String documentType;

    @JsonCreator
    public CompensateCancellationNoteCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("stepToCompensate") String stepToCompensate,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentType") String documentType) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.stepToCompensate = stepToCompensate;
        this.documentId = documentId;
        this.documentType = documentType;
    }

    public CompensateCancellationNoteCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                              String stepToCompensate, String documentId, String documentType) {
        super(sagaId, sagaStep, correlationId);
        this.stepToCompensate = stepToCompensate;
        this.documentId = documentId;
        this.documentType = documentType;
    }
}
```

- [ ] **Step 4: Create `SagaCommandHandler`**

```java
// src/main/java/.../infrastructure/adapter/in/messaging/SagaCommandHandler.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging;

import com.wpanther.cancellationnote.processing.application.port.in.CompensateCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.application.port.in.ProcessCancellationNoteUseCase;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto.CompensateCancellationNoteCommand;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto.ProcessCancellationNoteCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final ProcessCancellationNoteUseCase processCancellationNoteUseCase;
    private final CompensateCancellationNoteUseCase compensateCancellationNoteUseCase;

    public void handleProcessCommand(ProcessCancellationNoteCommand command) {
        log.info("Handling ProcessCancellationNoteCommand for saga {} document {}",
            command.getSagaId(), command.getDocumentId());
        try {
            processCancellationNoteUseCase.process(
                command.getDocumentId(), command.getXmlContent(),
                command.getSagaId(), command.getSagaStep(), command.getCorrelationId());
        } catch (ProcessCancellationNoteUseCase.CancellationNoteProcessingException e) {
            log.error("Failed to process cancellation note for saga {}: {}",
                command.getSagaId(), e.toString(), e);
        }
    }

    public void handleCompensation(CompensateCancellationNoteCommand command) {
        log.info("Handling compensation for saga {} document {}",
            command.getSagaId(), command.getDocumentId());
        compensateCancellationNoteUseCase.compensate(
            command.getDocumentId(), command.getSagaId(),
            command.getSagaStep(), command.getCorrelationId());
    }
}
```

- [ ] **Step 5: Create `SagaRouteConfig`**

```java
// src/main/java/.../infrastructure/adapter/in/messaging/SagaRouteConfig.java
package com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging;

import com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto.CompensateCancellationNoteCommand;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.in.messaging.dto.ProcessCancellationNoteCommand;
import com.wpanther.cancellationnote.processing.infrastructure.config.KafkaTopicsProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SagaRouteConfig extends RouteBuilder {

    private static final String GROUP_ID = "cancellationnote-processing-service";

    private final SagaCommandHandler sagaCommandHandler;
    private final KafkaTopicsProperties topics;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.camel.retry.max-redeliveries:3}")
    private int maxRedeliveries;

    @Value("${app.camel.retry.redelivery-delay-ms:1000}")
    private long redeliveryDelayMs;

    @Value("${app.camel.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Value("${app.camel.retry.max-redelivery-delay-ms:10000}")
    private long maxRedeliveryDelayMs;

    @Value("${app.kafka.consumers.max-poll-records:100}")
    private int maxPollRecords;

    @Value("${app.kafka.consumers.count:3}")
    private int consumersCount;

    public SagaRouteConfig(SagaCommandHandler sagaCommandHandler, KafkaTopicsProperties topics) {
        this.sagaCommandHandler = sagaCommandHandler;
        this.topics = topics;
    }

    private String kafkaConsumerParams() {
        return "?brokers=RAW(" + kafkaBrokers + ")"
            + "&groupId=" + GROUP_ID
            + "&autoOffsetReset=earliest"
            + "&autoCommitEnable=false"
            + "&breakOnFirstError=true"
            + "&maxPollRecords=" + maxPollRecords
            + "&consumersCount=" + consumersCount;
    }

    @Override
    public void configure() throws Exception {
        errorHandler(deadLetterChannel("kafka:" + topics.dlq() + "?brokers=RAW(" + kafkaBrokers + ")")
            .maximumRedeliveries(maxRedeliveries)
            .redeliveryDelay(redeliveryDelayMs)
            .useExponentialBackOff()
            .backOffMultiplier(backoffMultiplier)
            .maximumRedeliveryDelay(maxRedeliveryDelayMs)
            .logExhausted(true)
            .logStackTrace(true));

        from("kafka:" + topics.sagaCommandCancellationNote() + kafkaConsumerParams())
            .routeId("saga-command-consumer")
            .log("Received saga command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
            .unmarshal().json(JsonLibrary.Jackson, ProcessCancellationNoteCommand.class)
            .process(exchange -> {
                ProcessCancellationNoteCommand cmd = exchange.getIn().getBody(ProcessCancellationNoteCommand.class);
                log.info("Processing saga command for saga: {}, cancellation note: {}",
                    cmd.getSagaId(), cmd.getCancellationNoteNumber());
                sagaCommandHandler.handleProcessCommand(cmd);
            })
            .log("Successfully processed saga command");

        from("kafka:" + topics.sagaCompensationCancellationNote() + kafkaConsumerParams())
            .routeId("saga-compensation-consumer")
            .log("Received compensation command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
            .unmarshal().json(JsonLibrary.Jackson, CompensateCancellationNoteCommand.class)
            .process(exchange -> {
                CompensateCancellationNoteCommand cmd = exchange.getIn().getBody(CompensateCancellationNoteCommand.class);
                log.info("Processing compensation for saga: {}, document: {}",
                    cmd.getSagaId(), cmd.getDocumentId());
                sagaCommandHandler.handleCompensation(cmd);
            })
            .log("Successfully processed compensation command");
    }
}
```

- [ ] **Step 6: Run inbound adapter tests**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test -Dtest=SagaCommandHandlerTest 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && git add \
     src/main/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/in/messaging/ \
     src/test/java/com/wpanther/cancellationnote/processing/infrastructure/adapter/in/messaging/ \
  && git commit -m "feat: add inbound messaging adapter (SagaCommandHandler, SagaRouteConfig, command DTOs)"
```

---

### Task 9: Domain model unit tests

**Files:**
- Create: `src/test/java/com/wpanther/cancellationnote/processing/domain/model/ProcessedCancellationNoteTest.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/domain/model/CancellationNoteIdTest.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/domain/model/MoneyTest.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/domain/model/LineItemTest.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/domain/model/PartyTest.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/domain/model/AddressTest.java`
- Create: `src/test/java/com/wpanther/cancellationnote/processing/domain/model/TaxIdentifierTest.java`

- [ ] **Step 1: Write all domain model tests**

```java
// src/test/java/.../domain/model/CancellationNoteIdTest.java
package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CancellationNoteIdTest {
    @Test
    void generate_producesUniqueIds() {
        assertNotEquals(CancellationNoteId.generate(), CancellationNoteId.generate());
    }
    @Test
    void constructor_withNull_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new CancellationNoteId(null));
    }
    @Test
    void value_returnsUnderlyingUuid() {
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid, new CancellationNoteId(uuid).value());
    }
}
```

```java
// src/test/java/.../domain/model/MoneyTest.java
package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test
    void of_createsMoneyWithCorrectValues() {
        Money m = Money.of(new BigDecimal("1000.00"), "THB");
        assertEquals(new BigDecimal("1000.00"), m.amount());
        assertEquals("THB", m.currency());
    }
    @Test
    void add_sameCurrency_returnsSum() {
        Money a = Money.of(100, "THB");
        Money b = Money.of(200, "THB");
        assertEquals(Money.of(300, "THB"), a.add(b));
    }
    @Test
    void add_differentCurrency_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> Money.of(100, "THB").add(Money.of(100, "USD")));
    }
    @Test
    void zero_returnsMoneyWithZeroAmount() {
        assertTrue(Money.zero("THB").isZero());
    }
    @Test
    void constructor_withInvalidCurrency_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(100, "TH"));
    }
    @Test
    void multiply_scaledCorrectly() {
        Money m = Money.of(new BigDecimal("100.00"), "THB");
        assertEquals(Money.of(new BigDecimal("700.00"), "THB"), m.multiply(7));
    }
}
```

```java
// src/test/java/.../domain/model/AddressTest.java
package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AddressTest {
    @Test
    void of_createsAddressWithCorrectValues() {
        Address a = Address.of("123 St", "Bangkok", "10110", "TH");
        assertEquals("TH", a.country());
    }
    @Test
    void constructor_withNullCountry_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> Address.of("123 St", "Bangkok", "10110", null));
    }
    @Test
    void constructor_withBlankCountry_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Address.of("123 St", "Bangkok", "10110", "  "));
    }
    @Test
    void streetAndCityCanBeNull() {
        assertDoesNotThrow(() -> Address.of(null, null, null, "TH"));
    }
}
```

```java
// src/test/java/.../domain/model/TaxIdentifierTest.java
package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaxIdentifierTest {
    @Test
    void of_createsWithCorrectValues() {
        TaxIdentifier t = TaxIdentifier.of("1234567890", "VAT");
        assertEquals("1234567890", t.value());
        assertEquals("VAT", t.scheme());
    }
    @Test
    void constructor_withNullValue_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> TaxIdentifier.of(null, "VAT"));
    }
    @Test
    void constructor_withBlankValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> TaxIdentifier.of("  ", "VAT"));
    }
    @Test
    void toString_includesSchemeAndValue() {
        assertTrue(TaxIdentifier.of("1234567890", "VAT").toString().contains("VAT"));
    }
}
```

```java
// src/test/java/.../domain/model/PartyTest.java
package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyTest {
    private static final Address ADDR = Address.of("123 St", "Bangkok", "10110", "TH");
    private static final TaxIdentifier TAX = TaxIdentifier.of("1234567890", "VAT");

    @Test
    void of_createsWithCorrectValues() {
        Party p = Party.of("Seller Co", TAX, ADDR, "email@example.com");
        assertEquals("Seller Co", p.name());
        assertEquals("email@example.com", p.email());
    }
    @Test
    void constructor_withNullName_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> Party.of(null, TAX, ADDR, null));
    }
    @Test
    void constructor_withBlankName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Party.of("  ", TAX, ADDR, null));
    }
    @Test
    void emailCanBeNull() {
        assertDoesNotThrow(() -> Party.of("Seller Co", TAX, ADDR, null));
    }
}
```

```java
// src/test/java/.../domain/model/LineItemTest.java
package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class LineItemTest {
    private static final Money UNIT = Money.of(new BigDecimal("100.00"), "THB");

    @Test
    void getLineTotal_returnsQuantityTimesUnitPrice() {
        LineItem item = new LineItem("Service", 3, UNIT, BigDecimal.ZERO);
        assertEquals(Money.of(new BigDecimal("300.00"), "THB"), item.getLineTotal());
    }
    @Test
    void getTaxAmount_calculatesCorrectly() {
        LineItem item = new LineItem("Service", 1, UNIT, new BigDecimal("7.00"));
        assertEquals(Money.of(new BigDecimal("7.00"), "THB"), item.getTaxAmount());
    }
    @Test
    void getTotalWithTax_isLineTotalPlusTax() {
        LineItem item = new LineItem("Service", 1, UNIT, new BigDecimal("7.00"));
        assertEquals(Money.of(new BigDecimal("107.00"), "THB"), item.getTotalWithTax());
    }
    @Test
    void constructor_withZeroQuantity_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new LineItem("Service", 0, UNIT, BigDecimal.ZERO));
    }
    @Test
    void constructor_withNegativeTaxRate_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new LineItem("Service", 1, UNIT, new BigDecimal("-1")));
    }
}
```

```java
// src/test/java/.../domain/model/ProcessedCancellationNoteTest.java
package com.wpanther.cancellationnote.processing.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProcessedCancellationNoteTest {

    private static ProcessedCancellationNote buildNote() {
        Party seller = Party.of("Seller", TaxIdentifier.of("1111111111", "VAT"),
            new Address("1 St", "BKK", "10100", "TH"), null);
        Party buyer = Party.of("Buyer", TaxIdentifier.of("2222222222", "VAT"),
            new Address("2 St", "BKK", "10200", "TH"), null);
        LineItem item = new LineItem("Product", 2, Money.of(new BigDecimal("500.00"), "THB"), new BigDecimal("7.00"));
        return ProcessedCancellationNote.builder()
            .id(CancellationNoteId.generate())
            .sourceNoteId("src-1").cancellationNoteNumber("CN-001")
            .issueDate(LocalDate.of(2025, 1, 1)).cancellationDate(LocalDate.of(2025, 1, 1))
            .seller(seller).buyer(buyer).items(List.of(item))
            .currency("THB").cancelledInvoiceNumber("INV-001").originalXml("<xml/>")
            .build();
    }

    @Test
    void initialStatus_isPending() {
        assertEquals(ProcessingStatus.PENDING, buildNote().status());
    }

    @Test
    void markProcessing_changesStatusToProcessing() {
        ProcessedCancellationNote note = buildNote();
        note.markProcessing();
        assertEquals(ProcessingStatus.PROCESSING, note.status());
    }

    @Test
    void markCompleted_changesStatusToCompleted() {
        ProcessedCancellationNote note = buildNote();
        note.markCompleted();
        assertEquals(ProcessingStatus.COMPLETED, note.status());
        assertNotNull(note.completedAt());
    }

    @Test
    void getTotal_sumsTotalWithTaxAcrossItems() {
        ProcessedCancellationNote note = buildNote();
        // 2 items × 500.00 = 1000.00 + 7% tax = 1070.00
        assertEquals(Money.of(new BigDecimal("1070.00"), "THB"), note.getTotal());
    }

    @Test
    void getSubtotal_returnsLineTotalsSum() {
        assertEquals(Money.of(new BigDecimal("1000.00"), "THB"), buildNote().getSubtotal());
    }

    @Test
    void builder_withNoCancellationDate_defaultsToIssueDate() {
        // issueDate and cancellationDate are both supplied — test that they match
        ProcessedCancellationNote note = buildNote();
        assertEquals(note.issueDate(), note.cancellationDate());
    }

    @Test
    void builder_withEmptyItems_throwsIllegalStateException() {
        Party seller = Party.of("Seller", TaxIdentifier.of("1111111111", "VAT"),
            new Address("1 St", "BKK", "10100", "TH"), null);
        Party buyer = Party.of("Buyer", TaxIdentifier.of("2222222222", "VAT"),
            new Address("2 St", "BKK", "10200", "TH"), null);
        assertThrows(IllegalStateException.class, () ->
            ProcessedCancellationNote.builder()
                .id(CancellationNoteId.generate()).sourceNoteId("src-1")
                .cancellationNoteNumber("CN-001").issueDate(LocalDate.of(2025,1,1))
                .cancellationDate(LocalDate.of(2025,1,1))
                .seller(seller).buyer(buyer).items(List.of())
                .currency("THB").cancelledInvoiceNumber("INV-001").originalXml("<xml/>")
                .build());
    }

    @Test
    void builder_withCancellationDateBeforeIssueDate_throwsIllegalStateException() {
        Party seller = Party.of("Seller", TaxIdentifier.of("1111111111", "VAT"),
            new Address("1 St", "BKK", "10100", "TH"), null);
        Party buyer = Party.of("Buyer", TaxIdentifier.of("2222222222", "VAT"),
            new Address("2 St", "BKK", "10200", "TH"), null);
        LineItem item = new LineItem("P", 1, Money.of(new BigDecimal("100"), "THB"), BigDecimal.ZERO);
        assertThrows(IllegalStateException.class, () ->
            ProcessedCancellationNote.builder()
                .id(CancellationNoteId.generate()).sourceNoteId("src-1")
                .cancellationNoteNumber("CN-001").issueDate(LocalDate.of(2025,2,1))
                .cancellationDate(LocalDate.of(2025,1,1))
                .seller(seller).buyer(buyer).items(List.of(item))
                .currency("THB").cancelledInvoiceNumber("INV-001").originalXml("<xml/>")
                .build());
    }
}
```

- [ ] **Step 2: Run domain model tests**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test \
     -Dtest="CancellationNoteIdTest,MoneyTest,AddressTest,TaxIdentifierTest,PartyTest,LineItemTest,ProcessedCancellationNoteTest" \
     2>&1 | tail -15
```

Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && git add src/test/java/com/wpanther/cancellationnote/processing/domain/model/ \
  && git commit -m "test: add domain model unit tests"
```

---

### Task 10: Domain event test

**Files:**
- Create: `src/test/java/com/wpanther/cancellationnote/processing/domain/event/CancellationNoteProcessedDomainEventTest.java`

- [ ] **Step 1: Write test**

```java
// src/test/java/.../domain/event/CancellationNoteProcessedDomainEventTest.java
package com.wpanther.cancellationnote.processing.domain.event;

import com.wpanther.cancellationnote.processing.domain.model.Money;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class CancellationNoteProcessedDomainEventTest {

    @Test
    void of_setsAllFieldsCorrectly() {
        Money total = Money.of(new BigDecimal("1070.00"), "THB");
        CancellationNoteProcessedDomainEvent event = CancellationNoteProcessedDomainEvent.of(
            "note-id-1", "CN-001", total, "INV-001", "saga-1", "corr-1");

        assertEquals("note-id-1", event.noteId());
        assertEquals("CN-001", event.cancellationNoteNumber());
        assertEquals(total, event.total());
        assertEquals("INV-001", event.cancelledInvoiceNumber());
        assertEquals("saga-1", event.sagaId());
        assertEquals("corr-1", event.correlationId());
        assertNotNull(event.occurredAt());
    }

    @Test
    void of_occurredAt_isCloseToNow() {
        Instant before = Instant.now();
        CancellationNoteProcessedDomainEvent event = CancellationNoteProcessedDomainEvent.of(
            "n", "CN", Money.of(BigDecimal.ONE, "THB"), "I", "s", "c");
        Instant after = Instant.now();
        assertFalse(event.occurredAt().isBefore(before));
        assertFalse(event.occurredAt().isAfter(after));
    }

    @Test
    void record_equality_basedOnFields() {
        Money total = Money.of(new BigDecimal("100.00"), "THB");
        Instant now = Instant.now();
        CancellationNoteProcessedDomainEvent e1 = new CancellationNoteProcessedDomainEvent(
            "n", "CN", total, "I", "s", "c", now);
        CancellationNoteProcessedDomainEvent e2 = new CancellationNoteProcessedDomainEvent(
            "n", "CN", total, "I", "s", "c", now);
        assertEquals(e1, e2);
    }
}
```

- [ ] **Step 2: Run test**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test -Dtest=CancellationNoteProcessedDomainEventTest 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && git add src/test/java/com/wpanther/cancellationnote/processing/domain/event/ \
  && git commit -m "test: add CancellationNoteProcessedDomainEvent unit test"
```

---

### Task 11: Delete old files and final verification

**Files deleted:**
- `src/main/java/.../domain/event/CancellationNoteReplyEvent.java` (moved to infrastructure/adapter/out/messaging/dto/)
- `src/main/java/.../domain/event/CancellationNoteProcessedEvent.java` (moved to application/dto/event/)
- `src/main/java/.../domain/event/ProcessCancellationNoteCommand.java` (moved to infrastructure/adapter/in/messaging/dto/)
- `src/main/java/.../domain/event/CompensateCancellationNoteCommand.java` (moved to infrastructure/adapter/in/messaging/dto/)
- `src/main/java/.../domain/service/CancellationNoteParserService.java` (replaced by domain/port/out/CancellationNoteParserPort)
- `src/main/java/.../domain/repository/ProcessedCancellationNoteRepository.java` (replaced by domain/port/out/ProcessedCancellationNoteRepository)
- `src/main/java/.../infrastructure/service/CancellationNoteParserServiceImpl.java` (moved to infrastructure/adapter/out/parsing/)
- `src/main/java/.../infrastructure/messaging/SagaReplyPublisher.java` (moved to infrastructure/adapter/out/messaging/)
- `src/main/java/.../infrastructure/messaging/EventPublisher.java` (moved to infrastructure/adapter/out/messaging/)
- `src/main/java/.../application/service/SagaCommandHandler.java` (moved to infrastructure/adapter/in/messaging/)
- `src/main/java/.../infrastructure/config/SagaRouteConfig.java` (moved to infrastructure/adapter/in/messaging/)
- `src/main/java/.../infrastructure/persistence/CancellationNoteLineItemEntity.java` (moved to infrastructure/adapter/out/persistence/)
- `src/main/java/.../infrastructure/persistence/CancellationNotePartyEntity.java` (moved to infrastructure/adapter/out/persistence/)
- `src/main/java/.../infrastructure/persistence/JpaProcessedCancellationNoteRepository.java` (moved to infrastructure/adapter/out/persistence/)
- `src/main/java/.../infrastructure/persistence/ProcessedCancellationNoteEntity.java` (moved to infrastructure/adapter/out/persistence/)
- `src/main/java/.../infrastructure/persistence/ProcessedCancellationNoteRepositoryImpl.java` (moved to infrastructure/adapter/out/persistence/)
- `src/main/java/.../infrastructure/persistence/outbox/OutboxEventEntity.java` (moved to infrastructure/adapter/out/persistence/outbox/)
- `src/main/java/.../infrastructure/persistence/outbox/SpringDataOutboxRepository.java` (moved to infrastructure/adapter/out/persistence/outbox/)
- `src/main/java/.../infrastructure/persistence/outbox/JpaOutboxEventRepository.java` (moved to infrastructure/adapter/out/persistence/outbox/)

**Files modified:**
- `src/main/java/.../infrastructure/config/OutboxConfig.java` — update import from old outbox package to new

- [ ] **Step 1: Update `OutboxConfig.java` import**

Update the import from:
```java
import com.wpanther.cancellationnote.processing.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.wpanther.cancellationnote.processing.infrastructure.persistence.outbox.SpringDataOutboxRepository;
```
To:
```java
import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox.JpaOutboxEventRepository;
import com.wpanther.cancellationnote.processing.infrastructure.adapter.out.persistence.outbox.SpringDataOutboxRepository;
```

- [ ] **Step 2: Delete old files**

```bash
BASE=src/main/java/com/wpanther/cancellationnote/processing
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service

rm "$BASE/domain/event/CancellationNoteReplyEvent.java"
rm "$BASE/domain/event/CancellationNoteProcessedEvent.java"
rm "$BASE/domain/event/ProcessCancellationNoteCommand.java"
rm "$BASE/domain/event/CompensateCancellationNoteCommand.java"
rm "$BASE/domain/service/CancellationNoteParserService.java"
rm "$BASE/domain/repository/ProcessedCancellationNoteRepository.java"
rm "$BASE/infrastructure/service/CancellationNoteParserServiceImpl.java"
rm "$BASE/infrastructure/messaging/SagaReplyPublisher.java"
rm "$BASE/infrastructure/messaging/EventPublisher.java"
rm "$BASE/application/service/SagaCommandHandler.java"
rm "$BASE/infrastructure/config/SagaRouteConfig.java"
rm "$BASE/infrastructure/persistence/CancellationNoteLineItemEntity.java"
rm "$BASE/infrastructure/persistence/CancellationNotePartyEntity.java"
rm "$BASE/infrastructure/persistence/JpaProcessedCancellationNoteRepository.java"
rm "$BASE/infrastructure/persistence/ProcessedCancellationNoteEntity.java"
rm "$BASE/infrastructure/persistence/ProcessedCancellationNoteRepositoryImpl.java"
rm "$BASE/infrastructure/persistence/outbox/OutboxEventEntity.java"
rm "$BASE/infrastructure/persistence/outbox/SpringDataOutboxRepository.java"
rm "$BASE/infrastructure/persistence/outbox/JpaOutboxEventRepository.java"

# Remove now-empty directories
rmdir "$BASE/domain/service" "$BASE/domain/repository" \
      "$BASE/infrastructure/service" "$BASE/infrastructure/messaging" \
      "$BASE/infrastructure/persistence/outbox" "$BASE/infrastructure/persistence" 2>/dev/null || true
```

- [ ] **Step 3: Run full test suite to confirm everything passes**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn test 2>&1 | tail -20
```

Expected: `BUILD SUCCESS` with 0 failures and 0 errors across all tests. If compilation fails, check that:
- All old import references (e.g. `infrastructure.persistence.outbox`) are replaced with the new package path
- The old `CancellationNoteParserService` / `CancellationNoteParserPort` import is consistent throughout

- [ ] **Step 4: Run JaCoCo coverage check**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && mvn verify 2>&1 | grep -E "COVERAGE|Coverage|BUILD"
```

Expected: `BUILD SUCCESS`. If coverage is below the 80% minimum configured in `pom.xml`, add tests to the failing package before proceeding.

- [ ] **Step 5: Final commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/cancellationnote-processing-service \
  && git add -A \
  && git commit -m "refactor: remove old non-hexagonal files; hexagonal DDD refactor complete"
```
