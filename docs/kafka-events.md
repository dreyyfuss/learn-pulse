# LearnPulse — Kafka Event Catalogue
**Version:** 1.0
**Companion to:** `PRD.md` (§6)
**Broker:** Apache Kafka (single cluster — dev: local Docker; prod: managed)

---

## 1. Overview

LearnPulse uses Kafka as its single async backbone. There are **five** topics. The LMS Service and Certificate Service are the producers; consumers are split between the LMS Service (email), the Certificate Service (certificate generation), and the FastAPI AI service (indexing).

| Topic | Producer | Consumer Group(s) | Purpose |
|---|---|---|---|
| `course.published` | Spring Boot | `ai-service-indexer` (FastAPI) | Build per-course RAG knowledge base |
| `user.enrolled` | Spring Boot | `email-service` (Spring Boot) | Send welcome email |
| `module.unlocked` | Spring Boot | `email-service` (Spring Boot) | Send "next module ready" email |
| `course.completed` | Spring Boot | `certificate-service` (Certificate Service — separate app) | Generate PDF + persist certificate |
| `certificate.generated` | Certificate Service | `email-service` (LMS Service) | Send certificate delivery email |

---

## 2. Topic Configuration

| Topic | Partitions | Replication | Retention | Cleanup Policy | Key |
|---|---|---|---|---|---|
| `course.published` | 3 | 3 | 30 days | `delete` | `courseId` |
| `user.enrolled` | 6 | 3 | 30 days | `delete` | `userId` |
| `module.unlocked` | 6 | 3 | 30 days | `delete` | `userId` |
| `course.completed` | 6 | 3 | 90 days | `delete` | `enrolmentId` |
| `certificate.generated` | 6 | 3 | 90 days | `delete` | `certificateId` |

> **Why `userId` / `enrolmentId` keys?** Per-key ordering in Kafka is per-partition. Keying by user keeps that user's events ordered (e.g. `user.enrolled` before `module.unlocked`), which simplifies consumer logic. Keying `course.completed` by `enrolmentId` gives one-partition ordering per (user, course) pair so retries land in the same partition and consumer.

> **Dev defaults:** local single-broker Kafka uses `replication=1`. The Flyway-equivalent for Kafka is `infrastructure/kafka/topics.sh` (creates topics on first boot).

---

## 3. Common Envelope

Every event payload begins with the same metadata block:
```json
{
  "eventId":   "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "course.completed",
  "version":   1,
  "occurredAt": "2026-05-01T14:00:00Z",
  ...
}
```
- `eventId` — UUID v4. **Used as the idempotency key** by all consumers (PRD §6.4).
- `eventType` — matches the topic name.
- `version` — schema version, increment on breaking change.
- `occurredAt` — RFC 3339 UTC timestamp.

---

## 4. Topic Specifications

### 4.1 `course.published`

**Trigger:** `POST /api/courses/{id}/publish` succeeds.
**Producer:** `CoursePublishingService` (Spring Boot).
**Consumer:** FastAPI `aiokafka` consumer in group `ai-service-indexer`.
**Purpose:** Trigger one-time RAG indexing of all lessons in the course.

**Payload:**
```json
{
  "eventId":    "uuid-v4",
  "eventType":  "course.published",
  "version":    1,
  "occurredAt": "2026-05-01T10:00:00Z",
  "courseId":   456,
  "title":      "Intro to REST APIs",
  "instructor": { "id": 12, "fullName": "Ada Lovelace" },
  "lessons": [
    {
      "lessonId":          1,
      "title":             "What is REST?",
      "description":       "...",
      "contentType":       "ARTICLE",
      "moduleId":          11,
      "moduleTitle":       "Module 1: Foundations",
      "moduleDescription": "..."
    }
  ]
}
```

**Consumer behaviour (FastAPI):**
1. Skip if `course_id` already indexed (check `courses_indexed` table or vector store metadata).
2. For each lesson, chunk title + description + module context, embed, upsert into ChromaDB with `namespace = courseId`.
3. Mark course as indexed.
4. Commit offset.

Re-indexing is unnecessary because a course is locked once any learner starts it (PRD §5.2).

---

### 4.2 `user.enrolled`

**Trigger:** `POST /api/enrolments` (or admin equivalent) succeeds.
**Producer:** `EnrolmentService` (Spring Boot).
**Consumer:** Spring Boot `EmailConsumer` in group `email-service`.

**Payload:**
```json
{
  "eventId":    "uuid-v4",
  "eventType":  "user.enrolled",
  "version":    1,
  "occurredAt": "2026-05-01T11:00:00Z",
  "userId":     123,
  "courseId":   456,
  "enrolmentId": 789
}
```

**Consumer behaviour:**
1. Check `idempotency_log` for `eventId`. If present → ack and skip.
2. Load user + course + instructor from DB.
3. Send welcome email via Mailgun (template `enrolment_welcome`).
4. INSERT `idempotency_log(event_id, topic)`.
5. Commit offset.

---

### 4.3 `module.unlocked`

**Trigger:** A learner completes the last lesson in a module that is **not** the final module.
**Producer:** `ProgressService.completeLesson()` (Spring Boot), inside the lesson-completion transaction → emitted via the transactional outbox / after commit.
**Consumer:** Spring Boot `EmailConsumer` in group `email-service`.

**Payload:**
```json
{
  "eventId":             "uuid-v4",
  "eventType":           "module.unlocked",
  "version":             1,
  "occurredAt":          "2026-05-01T13:00:00Z",
  "userId":              123,
  "courseId":            456,
  "enrolmentId":         789,
  "unlockedModuleId":    790,
  "unlockedModuleTitle": "Module 2: Advanced Concepts",
  "unlockedModuleOrder": 2
}
```

**Consumer behaviour:** Send the "next module ready" email via Mailgun (template `module_unlocked`). Idempotency: `idempotency_log` keyed on `eventId`.

> **Important:** This event is **not** emitted when the final module is completed — `course.completed` is emitted instead.

---

### 4.4 `course.completed`

**Trigger:** A learner completes the final lesson of the final module. `enrolments.status` is set to `COMPLETED` in the same transaction.
**Producer:** `ProgressService.completeLesson()` (Spring Boot).
**Consumer:** Certificate Service `CertificateConsumer` (separate Spring Boot application) in group `certificate-service`.

**Payload:**
```json
{
  "eventId":     "uuid-v4",
  "eventType":   "course.completed",
  "version":     1,
  "occurredAt":  "2026-05-01T14:00:00Z",
  "userId":      123,
  "courseId":    456,
  "enrolmentId": 789,
  "completedAt": "2026-05-01T14:00:00Z"
}
```

**Consumer behaviour (exactly-once certificate flow):**
```
course.completed received
        │
        ▼
SELECT 1 FROM idempotency_log WHERE event_id = ?
        │
   ┌────┴─────┐
 found     not found
   │            │
  ack        BEGIN TX
  skip          │
            Render PDF (Thymeleaf + Flying Saucer)
                │
            Upload to S3:
              certificates/{userId}/{courseId}/{certUuid}.pdf
                │
            INSERT certificates(user_id, course_id, certificate_uuid, s3_url, issued_at)
              ── unique (user_id, course_id) catches double-process
                │
            INSERT idempotency_log(event_id, topic, processed_at)
                │
            COMMIT
                │
            Produce certificate.generated  (after commit, see §5)
                │
            ack
```

If the transaction fails before `COMMIT`, the message is **not acked** — Kafka will redeliver. If the certificate row insert fails the unique-constraint check (race with a duplicate consumer), the consumer logs and acks (no-op).

**Producer config:**
```properties
acks=all
enable.idempotence=true
max.in.flight.requests.per.connection=5
retries=2147483647
```

**Consumer config:**
```properties
enable.auto.commit=false
isolation.level=read_committed
max.poll.records=10
```
Manual offset commit is performed only after the DB transaction commits successfully (§5).

---

### 4.5 `certificate.generated`

**Trigger:** Certificate Service consumer commits the DB transaction in §4.4.
**Producer:** Certificate Service `CertificateConsumer`.
**Consumer:** Spring Boot `EmailConsumer` in group `email-service`.

**Payload:**
```json
{
  "eventId":       "uuid-v4",
  "eventType":     "certificate.generated",
  "version":       1,
  "occurredAt":    "2026-05-01T14:05:00Z",
  "userId":        123,
  "courseId":      456,
  "certificateId": "cert-uuid",
  "s3Url":         "certificates/123/456/cert-uuid.pdf",
  "issuedAt":      "2026-05-01T14:05:00Z"
}
```

**Why a separate topic?** Decouples PDF generation from email delivery. If Mailgun is unavailable, the certificate is already safely stored, and the email consumer retries without re-generating the PDF.

**Consumer behaviour:** Mailgun email (template `certificate_delivery`) with a download link routed through `GET /api/certificates/{id}/download`. Idempotent via `idempotency_log`.

---

## 5. Producer Patterns

### 5.1 Transactional Outbox (Recommended)
For events that must be emitted *iff* a DB write succeeds, use a transactional outbox. In this architecture the LMS Service owns `user.enrolled`, `module.unlocked`, and `course.completed`; the Certificate Service owns `certificate.generated`:

1. Inside the same DB transaction, INSERT a row into `outbox_events(payload, topic, status='PENDING')`.
2. A background `OutboxPublisher` (scheduled every 1 s) reads PENDING rows, publishes to Kafka, marks them `SENT`.

This avoids the dual-write problem (DB committed but Kafka publish failed).

Optional simpler alternative for the capstone scope: emit *after* the transaction commits using Spring's `TransactionalEventListener(phase = AFTER_COMMIT)`. Acceptable risk: a crash between commit and publish leaves the event un-emitted. Document this trade-off if chosen.

### 5.2 Idempotent Producer
All producers run with:
```properties
acks=all
enable.idempotence=true
```

---

## 6. Consumer Patterns

### 6.1 Idempotency Log
Single shared table:
```sql
CREATE TABLE idempotency_log (
  event_id     CHAR(36) PRIMARY KEY,
  topic        VARCHAR(80) NOT NULL,
  processed_at DATETIME(6) NOT NULL DEFAULT NOW(6)
);
```
Every consumer checks `event_id` before processing and inserts after.

### 6.2 Manual Offset Commits
All consumers disable auto-commit (`enable.auto.commit=false`). Offsets are committed only after the side effect (DB write or external API call) succeeds.

### 6.3 Retries & Dead-Letter Topic
Each consumer group has a corresponding `<topic>.dlq` topic. After `N=5` failed attempts (with exponential backoff: 1s, 4s, 16s, 1m, 5m), the message is sent to the DLQ and acked. An on-call dashboard surfaces DLQ depth.

| Source Topic | DLQ |
|---|---|
| `course.completed` | `course.completed.dlq` |
| `user.enrolled` | `user.enrolled.dlq` |
| `module.unlocked` | `module.unlocked.dlq` |
| `certificate.generated` | `certificate.generated.dlq` |
| `course.published` | `course.published.dlq` |

---

## 7. Schema Evolution

- Always include `version` in the envelope.
- Additive changes (new optional fields) → bump from v1 to v1, no consumer change required.
- Breaking changes (renamed/removed fields) → introduce `course.completed.v2` topic; run consumers for both during the migration window; retire v1 once producer cuts over.
- Schema files live in `infrastructure/kafka/schemas/<topic>.v<n>.json`.

---

## 8. Local Development

`docker-compose.dev.yml` brings up:
- 1 Kafka broker (`KRaft` mode, no Zookeeper).
- Kafka UI on `http://localhost:8085` for topic inspection.

`infrastructure/kafka/topics.sh` runs at first compose-up and creates all five topics + DLQs with the configs from §2.

---

## 9. Testing Strategy

| Concern | Tool |
|---|---|
| Unit tests for producers/consumers | `EmbeddedKafka` (`spring-kafka-test`) |
| Idempotency under redelivery | Force a duplicate `eventId`, assert single side effect |
| Exactly-once for certificates | Concurrent test (2 threads consume same event) → expect exactly 1 row in `certificates` |
| Cross-service indexing | Integration test publishes `course.published`, asserts FastAPI vector store has N chunks |

---

*End of Document*
