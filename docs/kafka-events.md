# LearnPulse — Kafka Event Catalogue
**Version:** 1.1
**Companion to:** `PRD.md` (§6)
**Broker:** Apache Kafka (single cluster — dev: local Docker; prod: managed)

---

## 1. Overview

LearnPulse uses Kafka as its single async backbone. There are **eight** topics. The Course Service and Certificate Service are the producers for domain events; the FastAPI AI Service is both a consumer (course generation requests) and a producer (generation results). Consumers are split between the User Service (email), the Certificate Service (certificate generation), the Course Service (generation results), and the FastAPI AI service (indexing + generation).

| Topic | Producer | Consumer Group(s) | Purpose |
|---|---|---|---|
| `course.published` | Course Service (Spring Boot) | `ai-service-indexer` (FastAPI) | Build per-course RAG knowledge base |
| `user.enrolled` | Course Service (Spring Boot) | `email-service` (User Service) | Send welcome email |
| `module.unlocked` | Course Service (Spring Boot) | `email-service` (User Service) | Send "next module ready" email |
| `course.completed` | Course Service (Spring Boot) | `certificate-service` (Certificate Service) | Generate PDF + persist certificate |
| `certificate.generated` | Certificate Service (Spring Boot) | `email-service` (User Service) | Send certificate delivery email |
| `course.generation.requested` | Course Service (Spring Boot) | `ai-service-course-generator` (FastAPI) | Trigger async AI course generation |
| `course.generation.completed` | FastAPI AI Service | `course-service-ai-consumer` (Course Service) | Persist generated course, modules, lessons, and quizzes |
| `course.generation.failed` | FastAPI AI Service | `course-service-ai-consumer` (Course Service) | Mark generation job as failed |

---

## 2. Topic Configuration

| Topic | Partitions | Replication | Retention | Cleanup Policy | Key |
|---|---|---|---|---|---|
| `course.published` | 3 | 3 | 30 days | `delete` | `courseId` |
| `user.enrolled` | 6 | 3 | 30 days | `delete` | `userId` |
| `module.unlocked` | 6 | 3 | 30 days | `delete` | `userId` |
| `course.completed` | 6 | 3 | 90 days | `delete` | `enrolmentId` |
| `certificate.generated` | 6 | 3 | 90 days | `delete` | `certificateId` |
| `course.generation.requested` | 3 | 3 | 30 days | `delete` | `jobId` |
| `course.generation.completed` | 3 | 3 | 30 days | `delete` | `jobId` |
| `course.generation.failed` | 3 | 3 | 30 days | `delete` | `jobId` |

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
  "courseId":   "550e8400-e29b-41d4-a716-446655440002",
  "title":      "Intro to REST APIs",
  "instructor": { "id": "550e8400-e29b-41d4-a716-446655440010", "fullName": "Ada Lovelace" },
  "lessons": [
    {
      "lessonId":          "550e8400-e29b-41d4-a716-446655440009",
      "title":             "What is REST?",
      "description":       "...",
      "contentType":       "ARTICLE",
      "contentKey":        "lessons/550e8400-e29b-41d4-a716-446655440009/content.mp4",
      "moduleId":          "550e8400-e29b-41d4-a716-44665544000b",
      "moduleTitle":       "Module 1: Foundations",
      "moduleDescription": "..."
    }
  ]
}
```

**Notes:** `contentKey` is the S3 object key for the lesson's main content file. It may be `null` if the instructor hasn't uploaded content yet; the AI indexer should embed title + description regardless.

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
  "userId":     "550e8400-e29b-41d4-a716-446655440001",
  "courseId":   "550e8400-e29b-41d4-a716-446655440002",
  "enrolmentId": "550e8400-e29b-41d4-a716-446655440003"
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
  "userId":              "550e8400-e29b-41d4-a716-446655440001",
  "courseId":            "550e8400-e29b-41d4-a716-446655440002",
  "enrolmentId":         "550e8400-e29b-41d4-a716-446655440003",
  "unlockedModuleId":    "550e8400-e29b-41d4-a716-44665544000c",
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
  "userId":      "550e8400-e29b-41d4-a716-446655440001",
  "courseId":    "550e8400-e29b-41d4-a716-446655440002",
  "enrolmentId": "550e8400-e29b-41d4-a716-446655440003",
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
  "userId":        "550e8400-e29b-41d4-a716-446655440001",
  "courseId":      "550e8400-e29b-41d4-a716-446655440002",
  "certificateId": "550e8400-e29b-41d4-a716-446655440099",
  "s3Key":         "certificates/550e8400.../cert-uuid.pdf",
  "issuedAt":      "2026-05-01T14:05:00Z",
  "downloadUrl":   "https://cdn.example.com/certificates/...?X-Amz-Signature=..."
}
```

**Why a separate topic?** Decouples PDF generation from email delivery. If Mailgun is unavailable, the certificate is already safely stored, and the email consumer retries without re-generating the PDF.

**Consumer behaviour:** Mailgun email (template `certificate_delivery`) with a download link routed through `GET /api/certificates/{id}/download`. Idempotent via `idempotency_log`.

---

### 4.6 `course.generation.requested`

**Trigger:** `POST /api/instructor/courses/generate` succeeds. A `CourseGenerationJob` row is created with `status = PENDING`.
**Producer:** `CourseGenerationProducer` (Course Service) via the transactional outbox — the event is written to `outbox_events` inside the same transaction that saves the job, then published by the `OutboxPublisher` scheduler.
**Consumer:** FastAPI `aiokafka` consumer in group `ai-service-course-generator`.
**Purpose:** Instruct the AI service to run the generation pipeline for this job.

**Payload:**
```json
{
  "eventId":      "uuid-v4",
  "eventType":    "course.generation.requested",
  "version":      1,
  "occurredAt":   "2026-05-22T10:00:00Z",
  "jobId":        "550e8400-e29b-41d4-a716-446655440010",
  "instructorId": "550e8400-e29b-41d4-a716-446655440001",
  "prompt":       "Build a course on REST APIs for beginners, with practical exercises"
}
```

**Consumer behaviour (FastAPI):**
1. Deserialise and validate event.
2. Run `CourseGenerationPipeline.generate(event)`:
   - **Step 1 (outline):** synchronous LLM call (`ChatGroq`, `llama-3.3-70b`) — produces a `CourseOutline` with 3–5 modules, each containing 3–5 lessons (title, description, orderIndex).
   - **Step 2 (content + quizzes):** parallel `llm.abatch()` — for every lesson simultaneously, generate Markdown article content (400–800 words) and a JSON quiz (3–5 questions, MCQ / TRUE_FALSE mix).
3. On success → publish `course.generation.completed` (§4.7) and commit offset.
4. On any exception → publish `course.generation.failed` (§4.8) with the exception message, then commit offset.

---

### 4.7 `course.generation.completed`

**Trigger:** `CourseGenerationPipeline` finishes successfully.
**Producer:** `GenerationEventProducer` (FastAPI AI Service) — published directly via `AIOKafkaProducer` (no outbox; the AI service has no relational DB).
**Consumer:** Spring Boot `CourseGenerationConsumer` in group `course-service-ai-consumer` — uses a dedicated `aiResultsListenerContainerFactory` with `AckMode.MANUAL_IMMEDIATE`.
**Purpose:** Persist the full generated course structure to the database and mark the job complete.

**Payload:**
```json
{
  "eventId":      "uuid-v4",
  "eventType":    "course.generation.completed",
  "version":      1,
  "occurredAt":   "2026-05-22T10:04:30Z",
  "jobId":        "550e8400-e29b-41d4-a716-446655440010",
  "instructorId": "550e8400-e29b-41d4-a716-446655440001",
  "course": {
    "title":       "REST APIs for Beginners",
    "description": "A practical introduction to building RESTful web services.",
    "category":    "Backend Development",
    "modules": [
      {
        "title":       "Module 1: Foundations",
        "description": "Core REST concepts and HTTP basics.",
        "orderIndex":  1,
        "lessons": [
          {
            "title":       "What is REST?",
            "description": "Origins and constraints of the REST architectural style.",
            "orderIndex":  1,
            "content":     "# What is REST?\n\nREST (Representational State Transfer)...",
            "quiz": {
              "title":        "Quiz: What is REST?",
              "passingScore": 70,
              "questions": [
                {
                  "questionText": "Which HTTP method is idempotent but not safe?",
                  "questionType": "MCQ",
                  "orderIndex":   1,
                  "options": [
                    { "optionText": "GET",    "isCorrect": false, "orderIndex": 1 },
                    { "optionText": "PUT",    "isCorrect": true,  "orderIndex": 2 },
                    { "optionText": "POST",   "isCorrect": false, "orderIndex": 3 },
                    { "optionText": "DELETE", "isCorrect": false, "orderIndex": 4 }
                  ]
                }
              ]
            }
          }
        ]
      }
    ]
  }
}
```

**Consumer behaviour (Course Service):**
1. Look up `CourseGenerationJob` by `jobId`; throw if not found.
2. Build and persist the full entity graph in one transaction: `Course` → `Module`s → `Lesson`s + `Quiz`es (with `QuizQuestion`s and `QuizOption`s).
3. For each lesson with non-blank `content`, upload the Markdown to S3 at key `lessons/{lessonId}/content.md` and set `lessons.content_key`.
4. Set `job.status = COMPLETED`, `job.course_id = <new courseId>`.
5. Ack the message.

> **No outbox / idempotency log:** Each job produces exactly one completed or failed event. If redelivery occurs, `buildCourse` will attempt to insert a duplicate `Course`; a retry guard on `job.status` (skip if already COMPLETED) would be a defensive improvement.

---

### 4.8 `course.generation.failed`

**Trigger:** Any unhandled exception during `CourseGenerationPipeline.generate()` in the AI service.
**Producer:** `GenerationEventProducer` (FastAPI AI Service).
**Consumer:** Spring Boot `CourseGenerationConsumer` in group `course-service-ai-consumer`.
**Purpose:** Surface the failure to the instructor via the job-status polling endpoint.

**Payload:**
```json
{
  "eventId":      "uuid-v4",
  "eventType":    "course.generation.failed",
  "version":      1,
  "occurredAt":   "2026-05-22T10:01:15Z",
  "jobId":        "550e8400-e29b-41d4-a716-446655440010",
  "instructorId": "550e8400-e29b-41d4-a716-446655440001",
  "reason":       "LLM returned malformed JSON after 3 retries"
}
```

**Consumer behaviour (Course Service):**
1. Look up `CourseGenerationJob` by `jobId`.
2. Set `job.status = FAILED`, `job.error_message = reason`.
3. Ack the message.

The `GET /api/instructor/courses/generate/{jobId}` endpoint returns this `errorMessage` to the frontend, which displays it inside the `AiGenerateModal`.

---

## 5. Producer Patterns

### 5.1 Transactional Outbox (Recommended)
For events that must be emitted *iff* a DB write succeeds, use a transactional outbox. In this architecture the Course Service owns `user.enrolled`, `module.unlocked`, and `course.completed`; the Certificate Service owns `certificate.generated`:

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
| `course.generation.requested` | `course.generation.requested.dlq` |
| `course.generation.completed` | `course.generation.completed.dlq` |
| `course.generation.failed` | `course.generation.failed.dlq` |

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

`infrastructure/kafka/topics.sh` runs at first compose-up and creates all eight topics + DLQs with the configs from §2.

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
