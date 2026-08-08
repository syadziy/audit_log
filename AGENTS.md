# AGENTS.md

## Project overview

`audit-log` is a Java 21 Spring Boot service for centralized, append-only user activity auditing.
It consumes validated Kafka events, persists them idempotently to PostgreSQL, exposes a
JWT-protected read-only investigation API, and reports terminal boundary failures to
`centralized_alert` over REST.

Core stack:

- Java 21 and Spring Boot 4.1
- Maven
- Spring Kafka with record acknowledgement, fixed retry, and dead-letter publishing
- Spring JDBC and PostgreSQL
- Flyway migrations
- `sdk-util` for response envelopes, REST exceptions, JWT security, trace IDs, ECS logging,
  OpenAPI, and timezone
- Actuator and Prometheus
- JUnit 5, Mockito, JaCoCo, and Testcontainers

Audit records are security-sensitive. Preserve confidentiality, integrity, idempotency, and a
clear ingestion trail ahead of convenience.

## Project structure

```text
src/main/java/com/mac/audit/
├── config/                 # Runtime beans, Kafka retry/DLT, properties
├── controller/             # Read-only audit REST endpoints
├── entities/
│   ├── constant/           # Outcomes and structured log fields
│   ├── dto/                # Kafka and REST contracts
│   └── model/              # Internal persistence/query models
├── repository/impl/        # JDBC persistence
├── service/impl/           # Audit ingestion and read logic
├── subscriber/             # Kafka listener boundary
└── utils/handler/          # HTTP/async boundary error logging and alert dispatch

src/main/resources/
├── db/migration/           # Versioned Flyway SQL
├── json/                   # Contract examples
├── application.yaml
└── application-local.yaml
```

Use package root `com.mac.audit`. Do not reuse `com.mac.alert` from the example context.

## Commands

Run from the `audit_log` directory:

```bash
mvn test
mvn clean verify
mvn spring-boot:run
```

Install `../sdk_util` with `mvn clean install` first when its artifact is unavailable locally.

## Architecture rules

- Audit ingestion enters through Kafka. Do not add an unauthenticated or public REST write API.
- Treat `eventId` as the immutable idempotency key.
- Application persistence is append-only. Do not add update/delete operations without an approved
  retention, correction, and authorization design.
- Keep controllers thin: parse/validate filters, delegate, and wrap responses.
- Keep SQL in repository implementations and use named parameters.
- Use `INSERT ... ON CONFLICT DO NOTHING` for at-least-once Kafka delivery.
- Keep query time ranges bounded and paginated.
- Use UTC `Instant` internally and `ZoneId` only when resolving date filters.
- New schema changes require forward-only Flyway migrations; never edit an applied migration.
- Organize every application YAML by major property group and precede each group with the
  three-line banner used in this repository (`# =========================`, an uppercase section
  name, and the same separator). Separate sections with one blank line and never change property
  hierarchy merely for formatting.

## Kafka rules

- Validate every event explicitly at the listener boundary.
- Use record acknowledgement and idempotent persistence before offset advancement.
- Invalid input is non-retryable. Transient infrastructure errors use configured retry and DLT.
- Kafka listener errors are not handled by MVC `GlobalExceptionHandler`; keep the dedicated
  `CommonErrorHandler` and async structured logging boundary.
- Terminal HTTP 5xx and exhausted asynchronous/Kafka failures must submit a sanitized alert to
  `centralized_alert`; alert delivery failure must never replace the original failure.
- Trace priority is payload `traceId`, Kafka key, then `eventId` for normal consumption. Error
  recovery also recognizes `X-Correlation-Id`.
- Do not log Kafka payloads or arbitrary metadata.
- Contract changes must update `src/main/resources/json/kafka-audit-event.json` and README.

## Audit data and privacy

- Never place passwords, JWTs, API keys, authorization headers, card data, secrets, or complete
  request/response bodies in audit metadata.
- Logs may contain audit event ID, source system, action, outcome, Kafka coordinates, and duplicate
  state. Do not log actor name, client IP, or metadata values.
- Treat `actorId`, `actorName`, `clientIp`, resource identifiers, and metadata as protected data.
- REST access must remain authenticated outside the local profile and should be further restricted
  by gateway/identity audit roles.
- Retention, archival, legal hold, encryption, backup, and database grants are operational controls;
  document environment-specific decisions rather than hardcoding them.

## API and validation

- All SDK-owned and service-owned client messages must be English.
- Use `ResponseHelper` and `ResponsePagingHelper`; do not create a second response envelope.
- Reuse SDK `ResourceNotFoundException` and global REST exception handling.
- `date` cannot be combined with `from` or `to`.
- `from` must be earlier than `to`, and the range cannot exceed `audit.query.max-range`.
- Keep default query window at 24 hours, maximum page size at 200, and offset non-negative unless a
  deliberate contract change is documented.
- Do not expose stack traces, SQL, exception classes, or internal paths.

## Logging and monitoring

- Use `StructuredLog`; never use `System.out` or `System.err`.
- Prefer ECS fields from SDK `LogFields` and service `AuditLogFields`.
- Preserve trace MDC at HTTP and Kafka boundaries and clear/restore it after execution.
- Never assume MDC propagates automatically to executors or virtual threads.
- Keep Actuator health/info/prometheus/metrics available through the documented security paths.
- Monitor consumer lag, DLT records, ingestion failures, duplicate rate, JDBC pool, query latency,
  and storage growth.

## Testing and coverage

Minimum JaCoCo line coverage for production business code is 90%, enforced by `mvn clean verify`.
The scope includes controllers, repository implementations, service implementations, subscribers,
handlers, and business utilities. DTOs, enums, generated code, application bootstrap, and trivial
configuration may be excluded only explicitly and justifiably.

Tests must verify outcomes, not merely execute lines. Required coverage includes:

- successful and duplicate ingestion;
- normalization and null metadata behavior;
- Kafka validation and trace fallbacks;
- repository JSON serialization/deserialization, filters, mapping, and count behavior;
- default/date/explicit query windows and invalid ranges;
- not-found response behavior;
- async boundary logging behavior;
- Flyway schema startup with Testcontainers when Docker is available.

Unit tests must not require Kafka, PostgreSQL, an identity provider, or network access.

## Security

- SDK security is enabled by default and disabled only by the local profile.
- Do not broaden `permit-all-paths` to include audit data endpoints.
- Do not add a REST write endpoint without explicit authentication, authorization, threat modeling,
  replay protection, and an approved reason to bypass Kafka.
- Any JWT, CORS, public path, role, or security-default change requires focused security tests.
- Validate all dynamic query criteria through named JDBC parameters; never concatenate values.

## Before finishing any task

1. Inspect `git status` and preserve unrelated user changes.
2. Run the smallest relevant tests during development.
3. Run `mvn clean verify` before handoff.
4. Run `git diff --check`.
5. Confirm migrations and Spring auto-config resources are correct.
6. Confirm all response/error messages are English.
7. Confirm no sensitive payload or metadata was added to logs.
8. Update README and JSON examples for contract/configuration changes.
9. Report skipped integration tests and the exact environmental reason.

## Never do

- Never mutate or delete an audit row through ordinary application behavior.
- Never log secrets or complete Kafka/HTTP payloads.
- Never acknowledge a record before durable/idempotent persistence succeeds.
- Never swallow Kafka listener failures; allow the error handler to retry or publish to DLT.
- Never disable the 90% coverage gate to make a build pass.
- Never commit `target/`, `.env`, credentials, tokens, or local database files.
