# Cancellation Note Processing Service — Hexagonal DDD Refactor

**Date:** 2026-04-27  
**Reference:** `taxinvoice-processing-service`  
**Scope:** Full structural and behavioural parity with taxinvoice; unit tests only.  
**Approach:** Single-pass implementation (Approach A — all changes in one PR).

---

## Goals

1. Restructure packages to match the hexagonal DDD layout used by `taxinvoice-processing-service`.
2. Add inbound/outbound port interfaces at both domain and application layers.
3. Fix behavioural gaps: idempotency, race-condition handling, `REQUIRES_NEW` on failure replies, `SagaStep` enum, typed Kafka topic config, `OutboxCleanupScheduler`, `HeaderSerializer`, `ProcessedCancellationNoteMapper`.
4. Add unit tests covering all layers (no integration tests).
5. Accessor style on domain model is **unchanged** (record-style `id()`, not JavaBeans `getId()`).

---

## Package Structure

```
com.wpanther.cancellationnote.processing/

domain/
  model/            ← unchanged (ProcessedCancellationNote, CancellationNoteId, Money,
  |                    LineItem, Party, Address, TaxIdentifier, ProcessingStatus)
  event/            ← CancellationNoteProcessedDomainEvent (renamed from CancellationNoteProcessedEvent)
  port/out/         ← ProcessedCancellationNoteRepository  (moved from domain/repository/)
                    ← CancellationNoteParserPort            (renamed from domain/service/CancellationNoteParserService)

application/
  port/in/          ← ProcessCancellationNoteUseCase       (new)
                    ← CompensateCancellationNoteUseCase     (new)
  port/out/         ← SagaReplyPort                        (new)
                    ← CancellationNoteEventPublishingPort   (new)
  dto/event/        ← CancellationNoteProcessedEvent       (application-layer DTO, moved from domain/event/)
  service/          ← CancellationNoteProcessingService    (refactored)

infrastructure/
  adapter/
    in/messaging/
      dto/          ← ProcessCancellationNoteCommand       (moved from domain/event/)
                    ← CompensateCancellationNoteCommand    (moved from domain/event/)
      SagaCommandHandler                                   (moved from application/service/)
      SagaRouteConfig                                      (moved from infrastructure/config/)
    out/messaging/
      dto/          ← CancellationNoteReplyEvent           (moved from domain/event/)
      SagaReplyPublisher       (implements SagaReplyPort)  (moved from infrastructure/messaging/)
      CancellationNoteEventPublisher                       (renamed from infrastructure/messaging/EventPublisher)
                     (implements CancellationNoteEventPublishingPort)
      HeaderSerializer                                     (new)
    out/parsing/
      CancellationNoteParserServiceImpl  (moved from infrastructure/service/)
    out/persistence/
      ProcessedCancellationNoteEntity                      (moved)
      CancellationNoteLineItemEntity                       (moved)
      CancellationNotePartyEntity                         (moved)
      ProcessedCancellationNoteMapper                      (new, extracted from RepositoryImpl)
      ProcessedCancellationNoteRepositoryImpl              (moved, delegates to Mapper)
      JpaProcessedCancellationNoteRepository               (moved)
      outbox/
        OutboxEventEntity                                  (moved)
        SpringDataOutboxRepository                         (moved)
        JpaOutboxEventRepository                           (moved)
        OutboxCleanupScheduler                             (new)
  config/
    KafkaTopicsProperties                                  (new)
    OutboxConfig                                           (unchanged)
```

---

## Section 1: Domain Layer Changes

### Unchanged
- All classes in `domain/model/` — `ProcessedCancellationNote` (including record-style accessors), `CancellationNoteId`, `Money`, `LineItem`, `Party`, `Address`, `TaxIdentifier`, `ProcessingStatus`.

### `domain/port/out/ProcessedCancellationNoteRepository`
Moved from `domain/repository/`. Interface signature unchanged.

### `domain/port/out/CancellationNoteParserPort`
Renamed from `domain/service/CancellationNoteParserService`. Method renamed:
- `parseCancellationNote(xml, sourceId)` → `parse(xml, sourceId)`

Inner exception class `CancellationNoteParsingException` gains static factory methods matching taxinvoice:
- `forEmpty()` — null/blank XML input
- `forOversized(byteSize, limitBytes)` — payload exceeds size limit
- `forTimeout(timeoutMs)` — JAXB unmarshal timed out
- `forInterrupted()` — executor thread interrupted
- `forUnmarshal(cause)` — JAXB/SAX exception during unmarshal
- `forUnexpectedRootElement(className)` — root element is not the expected type

### `domain/event/CancellationNoteProcessedDomainEvent`
Renamed from `CancellationNoteProcessedEvent`. Content unchanged. Rename avoids collision with the application-layer DTO of the same name.

### Deleted from `domain/event/`
`ProcessCancellationNoteCommand`, `CompensateCancellationNoteCommand`, `CancellationNoteReplyEvent` — these are messaging DTOs, not domain objects. They move to infrastructure adapter packages.

---

## Section 2: Application Layer Changes

### `application/port/in/ProcessCancellationNoteUseCase`
```java
void process(String documentId, String xmlContent,
             String sagaId, SagaStep sagaStep, String correlationId)
    throws CancellationNoteProcessingException;

class CancellationNoteProcessingException extends Exception { ... }
```

### `application/port/in/CompensateCancellationNoteUseCase`
```java
void compensate(String documentId, String sagaId,
                SagaStep sagaStep, String correlationId);

class CancellationNoteCompensationException extends RuntimeException { ... }
```

### `application/port/out/SagaReplyPort`
```java
void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId);
void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage);
void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId);
```
`publishFailure` contract: implemented with `REQUIRES_NEW` propagation so it commits independently even when the outer transaction is `ROLLBACK_ONLY`.

### `application/port/out/CancellationNoteEventPublishingPort`
```java
void publish(CancellationNoteProcessedDomainEvent event);
```

### `application/dto/event/CancellationNoteProcessedEvent`
Application-layer DTO (not a domain object). Fields: `noteId`, `cancellationNoteNumber`, `total`, `currency`, `cancelledInvoiceNumber`, `correlationId`. Used only by the outbound publisher adapter.

### `application/service/CancellationNoteProcessingService`
Implements `ProcessCancellationNoteUseCase` and `CompensateCancellationNoteUseCase`.

**Constructor injection:**
- `ProcessedCancellationNoteRepository`
- `CancellationNoteParserPort`
- `CancellationNoteEventPublishingPort`
- `SagaReplyPort`
- `MeterRegistry`
- `PlatformTransactionManager` (for REQUIRES_NEW template)

**`process()` behaviour:**
1. Idempotency check via `findBySourceNoteId(documentId)`:
   - `COMPLETED` → publish SUCCESS, return
   - `PROCESSING` → resume: `markCompleted()`, save, publish domain event, publish SUCCESS
   - Any other persisted status → `IllegalStateException`
2. Parse XML via `CancellationNoteParserPort.parse()` — on `CancellationNoteParsingException`: publish FAILURE (REQUIRES_NEW), throw `CancellationNoteProcessingException`
3. `markProcessing()`, save (PROCESSING state)
4. `markCompleted()`, save (COMPLETED state)
5. Publish `CancellationNoteProcessedDomainEvent` via `CancellationNoteEventPublishingPort`
6. Publish SUCCESS saga reply
7. `DuplicateKeyException` (any) — checked via `isSourceNoteIdViolation()` helper:
   - If constraint is **not** `uq_processed_cancellation_notes_source_note_id` → publish FAILURE immediately, throw
   - If constraint **is** `uq_processed_cancellation_notes_source_note_id` → race condition: re-check in REQUIRES_NEW template; if found → SUCCESS; if not found → FAILURE; always rethrow to prevent committing ROLLBACK_ONLY outer transaction
   - `isSourceNoteIdViolation()` requires both: message contains `uq_processed_cancellation_notes_source_note_id` AND SQLState is `23505`
8. `DataIntegrityViolationException` (non-DuplicateKey, e.g. check constraint, value-too-long) → publish FAILURE, throw
9. Generic exception → publish FAILURE (REQUIRES_NEW), throw

**`compensate()` behaviour:**
- `findBySourceNoteId` → if present, `deleteById`; if absent, log idempotent duplicate
- Publish COMPENSATED
- On any exception: publish FAILURE, throw `CancellationNoteCompensationException`

**Metrics (Micrometer counters + timer):**
- `cancellationnote.processing.success`
- `cancellationnote.processing.failure`
- `cancellationnote.processing.idempotent`
- `cancellationnote.processing.race_condition_resolved`
- `cancellationnote.compensation.success`
- `cancellationnote.compensation.idempotent`
- `cancellationnote.compensation.failure`
- `cancellationnote.processing.duration` (Timer)

---

## Section 3: Infrastructure Layer Changes

### `infrastructure/adapter/in/messaging/dto/ProcessCancellationNoteCommand`
Moved from `domain/event/`. `sagaStep` field type changes `String` → `SagaStep` enum. Accessor style changes to JavaBeans (`getSagaId()`, `getSagaStep()`, etc.) to match taxinvoice command DTOs. Jackson `@JsonProperty` annotations retained.

### `infrastructure/adapter/in/messaging/dto/CompensateCancellationNoteCommand`
Same changes as above.

### `infrastructure/adapter/in/messaging/SagaCommandHandler`
Moved from `application/service/`. Becomes `@Component`. Injects `ProcessCancellationNoteUseCase` and `CompensateCancellationNoteUseCase` (port interfaces).

Exception contract:
- `handleProcessCommand`: catches only `CancellationNoteProcessingException` (reply already committed to outbox) and returns normally so Camel commits the Kafka offset. All other exceptions propagate to DLC for retry.
- `handleCompensation`: does not catch — `CancellationNoteCompensationException` propagates to DLC for retry (deleteById is idempotent).

### `infrastructure/adapter/in/messaging/SagaRouteConfig`
Moved from `infrastructure/config/`. Injects `KafkaTopicsProperties` record. Uses `RAW()` wrapper for broker URLs. Retry/backoff values from `@Value` fields (`app.camel.retry.*`, `app.kafka.consumers.*`). Helper method `kafkaConsumerParams()` for shared consumer options.

### `infrastructure/adapter/out/messaging/dto/CancellationNoteReplyEvent`
Moved from `domain/event/`. Content unchanged.

### `infrastructure/adapter/out/messaging/SagaReplyPublisher`
Moved from `infrastructure/messaging/`. Implements `SagaReplyPort`.
- `publishSuccess` — `Propagation.MANDATORY` (unchanged)
- `publishFailure` — **changed to `Propagation.REQUIRES_NEW`** (critical fix)
- `publishCompensated` — `Propagation.MANDATORY` (unchanged)
- `toJson` replaced by `HeaderSerializer`

### `infrastructure/adapter/out/messaging/CancellationNoteEventPublisher`
Renamed from `infrastructure/messaging/EventPublisher`. Implements `CancellationNoteEventPublishingPort`. Method signature changed to accept `CancellationNoteProcessedDomainEvent`. Uses `HeaderSerializer` for JSON headers.

### `infrastructure/adapter/out/messaging/HeaderSerializer`
New utility `@Component`. Single method `toJson(Map<String,String>)`. Eliminates duplicated `toJson()` private methods in both publishers.

### `infrastructure/adapter/out/parsing/CancellationNoteParserServiceImpl`
Moved from `infrastructure/service/`. Implements `CancellationNoteParserPort`. Method renamed to `parse()`. Parser exceptions updated to use static factory methods.

### `infrastructure/adapter/out/persistence/ProcessedCancellationNoteMapper`
New `@Component`. Contains `toEntity(ProcessedCancellationNote)` and `toDomain(ProcessedCancellationNoteEntity)` logic extracted verbatim from `ProcessedCancellationNoteRepositoryImpl`. `RepositoryImpl` delegates to this mapper.

### `infrastructure/adapter/out/persistence/outbox/OutboxCleanupScheduler`
New `@Component`. Scheduled task: deletes published outbox events older than 24 hours. Identical to taxinvoice implementation.

### `infrastructure/config/KafkaTopicsProperties`
New `@ConfigurationProperties(prefix = "app.kafka.topics")` record:
```java
record KafkaTopicsProperties(
    String cancellationNoteProcessed,
    String dlq,
    String sagaCommandCancellationNote,
    String sagaCompensationCancellationNote,
    String sagaReplyCancellationNote
)
```

### `application.yml`
Add typed topic keys under `app.kafka.topics.*` and `app.camel.retry.*` / `app.kafka.consumers.*` properties.

---

## Section 4: Unit Tests

All under `src/test/java/com/wpanther/cancellationnote/processing/`.

### Domain model tests (`domain/model/`)
- `ProcessedCancellationNoteTest` — builder, invariants (empty items, cancellationDate before issueDate, currency mismatch on line item), `markProcessing/markCompleted/markFailed`, total/subtotal/tax calculations
- `CancellationNoteIdTest`
- `MoneyTest`
- `LineItemTest`
- `PartyTest`
- `AddressTest`
- `TaxIdentifierTest`
- `ProcessingStatusTest`

### Domain event tests (`domain/event/`)
- `CancellationNoteProcessedDomainEventTest`

### Command/event DTO tests (`infrastructure/adapter/in/messaging/dto/`)
- `ProcessCancellationNoteCommandTest` — Jackson JSON round-trip, field mapping, `SagaStep` enum deserialisation
- `CompensateCancellationNoteCommandTest` — same

### Reply event test (`infrastructure/adapter/out/messaging/dto/`)
- `CancellationNoteReplyEventTest` — static factories, JSON round-trip

### Application service tests (`application/service/`)
- `CancellationNoteProcessingServiceTest` — mocks all ports via Mockito:
  - Happy path (new document)
  - Idempotent: status `COMPLETED` → SUCCESS, no re-parse
  - Resume: status `PROCESSING` → markCompleted + save + event + SUCCESS
  - Illegal persisted status → `IllegalStateException`
  - Parse error → FAILURE published, `CancellationNoteProcessingException` thrown
  - `DuplicateKeyException` on source_note_id, record found → SUCCESS (REQUIRES_NEW)
  - `DuplicateKeyException` on source_note_id, record not found → FAILURE (REQUIRES_NEW)
  - `DuplicateKeyException` on other constraint → FAILURE immediately
  - `DataIntegrityViolationException` (non-duplicate) → FAILURE
  - Generic exception → FAILURE (REQUIRES_NEW)
  - Compensation happy path
  - Compensation idempotent (not found)
  - Compensation failure → `CancellationNoteCompensationException` thrown
  - Metrics incremented correctly for each path

### `SagaCommandHandler` tests (`infrastructure/adapter/in/messaging/`)
- `SagaCommandHandlerTest` — `CancellationNoteProcessingException` swallowed; other exceptions propagate; correct use case methods called

### `SagaRouteConfig` tests (`infrastructure/adapter/in/messaging/`)
- `SagaRouteConfigTest` — Camel route wiring with mock endpoint

### Outbound messaging tests (`infrastructure/adapter/out/messaging/`)
- `SagaReplyPublisherTest` — correct topic, status, and outbox fields for success/failure/compensated
- `SagaReplyPublisherTransactionTest` — `publishFailure` uses `REQUIRES_NEW`; `publishSuccess` uses `MANDATORY`
- `CancellationNoteEventPublisherTest` — correct topic, payload, headers
- `HeaderSerializerTest` — JSON serialisation correctness and null-safety

### Parser tests (`infrastructure/adapter/out/parsing/`)
- `CancellationNoteParserServiceImplTest` — valid XML → correct domain model; empty → `forEmpty()`; oversized → `forOversized()`; malformed → `forUnmarshal()`; wrong root element → `forUnexpectedRootElement()`

### Persistence tests (`infrastructure/adapter/out/persistence/`)
- `ProcessedCancellationNoteMapperTest` — `toEntity`/`toDomain` round-trip; null-safety for optional fields
- `ProcessedCancellationNoteRepositoryImplTest` — H2-backed; save/findById/findBySourceNoteId/findByCancellationNoteNumber/deleteById
- `ProcessedCancellationNoteEntityTest`
- `CancellationNoteLineItemEntityTest`
- `CancellationNotePartyEntityTest`
- `outbox/JpaOutboxEventRepositoryTest`
- `outbox/OutboxCleanupSchedulerTest`
- `outbox/OutboxEventEntityTest`

### Smoke test
- `CancellationNoteProcessingServiceApplicationTest` — unchanged

---

## Files Deleted After Refactor

| Old path | Reason |
|----------|--------|
| `domain/repository/ProcessedCancellationNoteRepository.java` | Moved to `domain/port/out/` |
| `domain/service/CancellationNoteParserService.java` | Renamed to `CancellationNoteParserPort` in `domain/port/out/` |
| `domain/event/CancellationNoteProcessedEvent.java` | Renamed to `CancellationNoteProcessedDomainEvent` |
| `domain/event/ProcessCancellationNoteCommand.java` | Moved to `infrastructure/adapter/in/messaging/dto/` |
| `domain/event/CompensateCancellationNoteCommand.java` | Moved to `infrastructure/adapter/in/messaging/dto/` |
| `domain/event/CancellationNoteReplyEvent.java` | Moved to `infrastructure/adapter/out/messaging/dto/` |
| `application/service/SagaCommandHandler.java` | Moved to `infrastructure/adapter/in/messaging/` |
| `infrastructure/config/SagaRouteConfig.java` | Moved to `infrastructure/adapter/in/messaging/` |
| `infrastructure/messaging/EventPublisher.java` | Renamed to `CancellationNoteEventPublisher` in `infrastructure/adapter/out/messaging/` |
| `infrastructure/messaging/SagaReplyPublisher.java` | Moved to `infrastructure/adapter/out/messaging/` |
| `infrastructure/service/CancellationNoteParserServiceImpl.java` | Moved to `infrastructure/adapter/out/parsing/` |
| All `infrastructure/persistence/*.java` | Moved to `infrastructure/adapter/out/persistence/` |

---

## New Migration Required

`V3__Add_unique_constraint_source_note_id.sql` must be added:
```sql
ALTER TABLE processed_cancellation_notes
ADD CONSTRAINT uq_processed_cancellation_notes_source_note_id
UNIQUE (source_note_id);
```
The existing `idx_cancellation_source_note_id` non-unique index can be dropped (the unique constraint creates an equivalent implicit index), or kept as a named alias for clarity. The `isSourceNoteIdViolation` helper in `CancellationNoteProcessingService` matches against this exact constraint name.

---

## Constraints

- Domain model accessor style **not changed** (record-style kept: `id()`, `status()`, etc.)
- No integration tests
- No REST API (service is event-driven only)
- V1/V2 Flyway migrations unchanged; V3 adds the unique constraint above
- Lombok annotation processor order (before MapStruct) must be preserved in `pom.xml`
