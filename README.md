# Centralized Audit Log Service

`audit_log` is a Java 21 Spring Boot service that receives user-activity events from Kafka and
stores them in an append-only PostgreSQL audit table. It also exposes authenticated, read-only REST
endpoints for audit investigation and monitoring.

JVM, JDBC session, persisted timestamps, logs, and API timestamps use UTC by default through
`APP_TIMEZONE=UTC`.

## Main behavior

- Consumes `AuditEventRequest` from `centralized-audit.requested`.
- Validates every Kafka payload before persistence.
- Uses `eventId` as an idempotency key; duplicate delivery does not create a second row.
- Retries transient Kafka listener failures and publishes exhausted records to a dead-letter topic.
- Sends failures from HTTP and asynchronous/Kafka boundaries to `centralized_alert` over REST.
- Stores flexible metadata as PostgreSQL `JSONB`.
- Exposes paginated filtering by time, source, actor, action, resource, and outcome.
- Uses `sdk-util` response envelopes, JWT security, global REST exceptions, trace IDs, ECS logging,
  OpenAPI defaults, and application timezone.
- Exposes health and Prometheus metrics through Actuator.
- Enforces at least 90% JaCoCo line coverage for production business code.

The service deliberately has no REST write endpoint. Audit creation only enters through Kafka so
business services can publish asynchronously and the ingestion contract remains centralized.

## Technology

- Java 21
- Spring Boot 4.1
- Spring Kafka
- Spring JDBC
- PostgreSQL and Flyway
- `sdk-util` 1.0.0
- JaCoCo, JUnit 5, Mockito, and Testcontainers

## Architecture

```text
Business service
    -> Kafka: centralized-audit.requested
    -> AuditLogConsumer
    -> AuditLogService
    -> INSERT ... ON CONFLICT DO NOTHING
    -> PostgreSQL audit_log

HTTP 5xx / exhausted Kafka failure
    -> POST centralized_alert /api/v1/alert
    -> email delivery managed by centralized_alert

Investigator / monitoring client
    -> JWT-protected REST API
    -> AuditLogController
    -> filtered, paginated read
```

The application logs event identifiers and classification fields, but never logs actor names,
client IP addresses, or arbitrary metadata values.

## Project structure

```text
src/main/java/com/mac/audit/
├── config/                 # Runtime beans, Kafka retry/DLT, typed properties
├── controller/             # Read-only REST API
├── entities/
│   ├── constant/           # Outcome and ECS field constants
│   ├── dto/                # Kafka request and REST response contracts
│   └── model/              # Persistence and query models
├── repository/
│   └── impl/               # PostgreSQL JDBC implementation
├── service/
│   └── impl/               # Ingestion, normalization, and response mapping
├── subscriber/             # Kafka listener boundary
└── utils/handler/          # Async/Kafka failure logging boundary

src/main/resources/
├── db/migration/           # Flyway schema and indexes
├── json/                   # Kafka and REST examples
├── application.yaml
└── application-local.yaml
```

## Prerequisites

1. Java 21.
2. Maven 3.9 or newer.
3. `sdk-util` installed locally when it is not available from a Maven repository:

   ```bash
   cd ../sdk_util
   mvn clean install
   ```

4. PostgreSQL 16 and Kafka, or equivalent compatible versions.

## Configuration

Copy values from `.env.example` to your local environment. Important variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `9003` | HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/audit_log` | PostgreSQL connection |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `AUDIT_KAFKA_TOPIC` | `centralized-audit.requested` | Ingestion topic |
| `AUDIT_KAFKA_DLT_TOPIC` | `centralized-audit.requested.dlt` | Exhausted event topic |
| `AUDIT_KAFKA_MAX_RETRIES` | `2` | Retries after the first listener attempt |
| `AUDIT_QUERY_MAX_RANGE` | `P31D` | Maximum REST query time range |
| `AUDIT_ERROR_ALERT_ENABLED` | `true` | Enable centralized error alerts |
| `CENTRALIZED_ALERT_URL` | `http://localhost:9001/api/v1/alert` | Central alert API endpoint |
| `AUDIT_ERROR_ALERT_RECIPIENTS` | `ops@example.com` | Comma-separated alert recipients |
| `AUDIT_ERROR_ALERT_AUTHORIZATION_HEADER` | empty | Full outbound Authorization header |
| `OAUTH2_ISSUER_URI` | `http://localhost:9005` | `usermanagement` JWT issuer |

The default `local` profile disables SDK security for development. Use a non-local profile and a
reachable `usermanagement` issuer for shared environments.

## Run

```bash
mvn spring-boot:run
```

Flyway creates `audit_log` and its search indexes during startup.

## Kafka contract

Topic: `centralized-audit.requested`

```json
{
  "eventId": "11111111-1111-1111-1111-111111111111",
  "sourceSystem": "billing-service",
  "occurredAt": "2026-08-09T01:30:00Z",
  "actorId": "user-123",
  "actorName": "Ada Lovelace",
  "action": "invoice.approved",
  "resourceType": "invoice",
  "resourceId": "INV-2026-001",
  "outcome": "SUCCESS",
  "traceId": "9c43e2c1-4220-4ff6-8078-fb7413d62fb6",
  "clientIp": "192.0.2.10",
  "metadata": {
    "channel": "web",
    "previousStatus": "PENDING",
    "newStatus": "APPROVED"
  }
}
```

`outcome` accepts `SUCCESS`, `FAILURE`, `DENIED`, or `UNKNOWN`. Do not publish passwords, tokens,
authorization headers, payment card data, or full request bodies inside `metadata`.

Invalid input is not retried. Transient failures are retried using fixed backoff and then sent to
the configured DLT. Kafka uses record acknowledgement, so a successful idempotent insert completes
the record before its offset advances.

## Error alert integration

The service submits a priority-1 TEXT alert to `centralized_alert` when an HTTP request escapes with
an exception or finishes with a 5xx response, and when asynchronous processing reports a terminal
failure. The Kafka retry/DLT recoverer uses that asynchronous boundary, so an exhausted listener
failure also creates an alert. Validation errors and other 4xx responses do not create alerts.

Alert delivery is best effort: a timeout, connection error, or non-2xx response is logged without
masking or retrying the original failure. Configure `AUDIT_ERROR_ALERT_AUTHORIZATION_HEADER` when
the central endpoint requires a service token. Secrets and original exception details are excluded
from the alert body. See `src/main/resources/json/error-alert-request.json` for the outbound contract.

## REST API

### Find audit records

```http
GET /api/v1/audit-logs?date=2026-08-09&actorId=user-123&outcome=SUCCESS&limit=50&offset=0
```

Supported filters:

- `date`, or the `from` and `to` pair. `date` cannot be combined with `from`/`to`.
- `sourceSystem`, `actorId`, `action`, `resourceType`, `resourceId`, and `outcome`.
- `limit` from 1 through 200 and non-negative `offset`.

If no time is supplied, the API queries the previous 24 hours. Ranges cannot exceed
`audit.query.max-range`.

### Find one audit record

```http
GET /api/v1/audit-logs/11111111-1111-1111-1111-111111111111
```

All REST responses use `ResponseDTO` from `sdk-util`. Detailed examples are indexed at
`src/main/resources/json/index.json`.

## Build, test, and coverage

```bash
mvn test
mvn clean verify
```

`mvn clean verify` fails below 90% line coverage. The HTML report is generated at
`target/site/jacoco/index.html`. The PostgreSQL/Flyway integration test uses Testcontainers when
Docker is available and is skipped otherwise; unit tests do not require Docker or network access.

## Docker

Build JAR terlebih dahulu, kemudian buat runtime image Java 21:

```bash
mvn clean package
docker build -t audit-log:1.0.0 .
docker run --rm --env-file .env -p 9003:9003 audit-log:1.0.0
```

Isi `.env` dari `.env.example`. Gunakan hostname service pada Docker network untuk PostgreSQL,
Kafka, dan `centralized_alert`; jangan gunakan `localhost` untuk dependency container lain.

## Operational notes

- Path `/internal/**` tidak memerlukan JWT dan hanya boleh tersedia pada trusted internal network;
  public ingress dan API Gateway tidak boleh membuat route ke path tersebut.
- Rows are append-only from the application perspective; there are no update or delete APIs.
- Retention, archival, legal hold, and database access policies must be defined per environment.
- Restrict REST access to authorized audit/operations roles at the identity and gateway layers.
- Back up the database and monitor Kafka consumer lag, DLT growth, database pool health, and
  ingestion errors.
- `X-Correlation-Id`, Kafka key, then `eventId` are used in that order as trace fallbacks.

Contribution and implementation requirements are documented in `AGENTS.md`.
