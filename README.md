# Cancellation Note Processing Service

Spring Boot microservice for processing Thai e-Tax cancellation notes as part of a Saga-based orchestration pattern.

## Tech Stack

- Java 21
- Spring Boot 3.2.5
- Apache Camel 4.14.4
- PostgreSQL
- Kafka
- Eureka
- Flyway

## Build & Run

```bash
# Build dependencies first
cd ../../../teda && mvn clean install
cd ../../../saga-commons && mvn clean install

# Build
mvn clean package

# Run (requires PostgreSQL on localhost:5432, Kafka on localhost:9092)
mvn spring-boot:run

# Run with Docker test environment
DB_PORT=5433 KAFKA_BROKERS=localhost:9093 mvn spring-boot:run

# Run tests
mvn test
mvn verify
```

## Port

**8089**

## Database

**cancellationnoteprocess_db**

## Kafka Topics

| Topic | Direction | Purpose |
|--------|-----------|---------|
| `saga.command.cancellation-note` | Consumer | Process commands from orchestrator |
| `saga.compensation.cancellation-note` | Consumer | Compensation commands |
| `saga.reply.cancellation-note` | Producer | Reply to orchestrator |
| `cancellationnote.processed` | Producer | Notification events |

## Architecture

Follows the same Saga orchestration pattern as taxinvoice-processing-service:

```
Orchestrator → saga.command.cancellation-note → Camel → SagaCommandHandler
                                                              ↓
                                           CancellationNoteProcessingService
                                                              ↓
                                           Parse XML → Save to DB
                                                              ↓
                                           EventPublisher → Outbox → (Debezium CDC) → Kafka
                                                              ↓
                                           saga.reply.cancellation-note (SUCCESS/FAILURE/COMPENSATED)
                                           cancellationnote.processed → Notification Service
```

## Key Differences from Tax Invoice Service

| Aspect | Tax Invoice | Cancellation Note |
|---------|-------------|-------------------|
| Root Type | TaxInvoice_CrossIndustryInvoiceType | CancellationNote_CrossIndustryInvoiceType |
| JAXB Packages | taxinvoice.rsm.impl, taxinvoice.ram.impl | cancellationnote.rsm.impl, cancellationnote.ram.impl |
| Aggregate | ProcessedTaxInvoice | ProcessedCancellationNote |
| Tables | processed_tax_invoices, tax_invoice_parties, tax_invoice_line_items | processed_cancellation_notes, cancellation_note_parties, cancellation_note_line_items |
| Unique Field | invoiceNumber | cancellationNoteNumber |
| Additional Field | - | cancelledInvoiceNumber |
