# Cancellation Note Processing Service

Microservice for processing and enriching Thai e-Tax cancellation notes as a participant in the Saga Orchestration pipeline.

## Overview

The Cancellation Note Processing Service is responsible for:

- **Receiving** `ProcessCancellationNoteCommand` from the Orchestrator Service via Kafka
- **Parsing** XML cancellation notes using the teda library v1.0.0
- **Calculating** totals, taxes, and other derived values
- **Persisting** processed cancellation note data to PostgreSQL
- **Logging** compensation events to a permanent audit trail
- **Replying** to the Orchestrator with success or failure via the Transactional Outbox pattern

## Architecture

### Domain-Driven Design (Hexagonal)

```
domain/
├── model/        # ProcessedCancellationNote (aggregate root), value objects, CompensationLogEntry
├── port/
│   └── out/      # Repository and parser interfaces (ProcessedCancellationNoteRepository,
│                 #   CancellationNoteParserPort)
└── event/        # Kafka DTOs

application/
├── service/      # CancellationNoteProcessingService, SagaCommandHandler
└── dto/          # Command/Reply DTOs

infrastructure/
├── adapter/out/persistence/  # JPA entities, Spring Data repositories, mappers
├── messaging/                # EventPublisher, SagaReplyPublisher
├── service/                  # CancellationNoteParserServiceImpl (teda XML parsing)
└── config/                   # Camel routes, OutboxConfig
```

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Message Routing | Apache Camel 4.14.4 |
| Database | PostgreSQL (`cancellationnoteprocess_db`) |
| Messaging | Apache Kafka |
| Service Discovery | Netflix Eureka |
| Database Migration | Flyway |
| XML Parsing | teda Library v1.0.0 |
| Metrics | Micrometer / Prometheus |

### Saga Orchestration

This service is a **Saga participant**. It does not initiate sagas — it responds to commands from the Orchestrator Service.

```
[Orchestrator] → saga.command.cancellation-note → [CancellationNoteRouteConfig]
                                                    ↓
                                            SagaCommandHandler
                                                    ↓
                                            CancellationNoteProcessingService
                                            (parse → save → outbox)
                                                    ↓
                                            outbox_events → Debezium CDC
                                                    ↓
                                            saga.reply.cancellation-note → [Orchestrator]
```

**Compensation** (`saga.compensation.cancellation-note`): Hard-deletes the `ProcessedCancellationNote` row by `documentId` and writes a `CompensationLogEntry` atomically, then replies COMPENSATED.

## Database Schema

Five tables managed by Flyway:

| Table | Purpose |
|-------|---------|
| `processed_cancellation_notes` | Main cancellation note data (aggregate root) |
| `cancellation_note_parties` | Seller and buyer information |
| `cancellation_note_line_items` | Line items with quantities and prices |
| `outbox_events` | Transactional outbox for Debezium CDC |
| `compensation_log` | Permanent audit trail of saga compensation events (never deleted) |

## Kafka Topics

### Consumed

| Topic | Command Class | Handler |
|-------|--------------|---------|
| `saga.command.cancellation-note` | `ProcessCancellationNoteCommand` | `SagaCommandHandler.handleProcessCommand()` |
| `saga.compensation.cancellation-note` | `CompensateCancellationNoteCommand` | `SagaCommandHandler.handleCompensation()` |

Both routes use `groupId=cancellationnote-processing-service`, 3 consumers, manual acknowledgment.

### Published (via Outbox)

| Topic | Event Class | Trigger |
|-------|------------|---------|
| `saga.reply.cancellation-note` | `CancellationNoteReplyEvent` | After every process/compensate call |
| `cancellationnote.processed` | `CancellationNoteProcessedEvent` | After successful processing (notification) |

Dead letter: `cancellationnote.processing.dlq`

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `cancellationnoteprocess_db` | Database name |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `KAFKA_BROKERS` | `localhost:9092` | Kafka bootstrap servers |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka server URL |

Service runs on port **8089**.

## Running the Service

### Prerequisites

1. PostgreSQL running with `cancellationnoteprocess_db` database
2. Kafka broker running
3. Eureka server running (optional)
4. teda library installed: `cd ../../../teda && mvn clean install`
5. saga-commons library installed: `cd ../../../saga-commons && mvn clean install`

### Build

```bash
mvn clean package
```

### Run Locally

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=cancellationnoteprocess_db
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export KAFKA_BROKERS=localhost:9092

mvn spring-boot:run
```

### Run with Docker

```bash
docker build -t cancellationnote-processing-service:latest .

docker run -p 8089:8089 \
  -e DB_HOST=postgres \
  -e DB_PORT=5432 \
  -e DB_NAME=cancellationnoteprocess_db \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  -e KAFKA_BROKERS=kafka:29092 \
  cancellationnote-processing-service:latest
```

## API Endpoints

This service is **event-driven only** — no REST endpoints for business operations.

### Actuator

```
GET http://localhost:8089/actuator/health
GET http://localhost:8089/actuator/prometheus
GET http://localhost:8089/actuator/camelroutes
```

## Testing

```bash
# Unit tests
mvn test

# Unit tests + JaCoCo coverage check
mvn verify

# Single test
mvn test -Dtest=CancellationNoteProcessingServiceTest

# Integration tests (requires Docker/Podman)
mvn verify -P integration
```

### Database Migrations

```bash
mvn flyway:migrate
mvn flyway:info
```

## Monitoring

### Custom Metrics (Prometheus)

| Metric | Description |
|--------|-------------|
| `cancellationnote.processing.success` | Successfully processed cancellation notes |
| `cancellationnote.processing.failure` | Failed processing attempts |
| `cancellationnote.processing.idempotent` | Duplicate commands handled idempotently |
| `cancellationnote.processing.race_condition_resolved` | Concurrent-insert races resolved as idempotent |
| `cancellationnote.processing.duration` | Processing time histogram |
| `cancellationnote.compensation.success` | Successful compensations |
| `cancellationnote.compensation.idempotent` | Compensation commands for already-absent records |
| `cancellationnote.compensation.failure` | Failed compensation attempts |

## Key Differences from Tax Invoice Service

| Aspect | Tax Invoice | Cancellation Note |
|---------|-------------|-------------------|
| Root Type | TaxInvoice_CrossIndustryInvoiceType | CancellationNote_CrossIndustryInvoiceType |
| JAXB Packages | taxinvoice.rsm.impl, taxinvoice.ram.impl | cancellationnote.rsm.impl, cancellationnote.ram.impl |
| Aggregate | ProcessedTaxInvoice | ProcessedCancellationNote |
| Tables | processed_tax_invoices, tax_invoice_parties, tax_invoice_line_items | processed_cancellation_notes, cancellation_note_parties, cancellation_note_line_items |
| Unique Field | invoiceNumber | cancellationNoteNumber |
| Additional Field | - | cancelledInvoiceNumber |

## License

MIT License

## Contact

Maintained by wpanther (rabbit_roger@yahoo.com)