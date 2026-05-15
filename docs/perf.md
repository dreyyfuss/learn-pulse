# LearnPulse — Performance Report
**Companion to:** `plan.md §8.2`

---

## Caching Strategy

### Redis Cache Layers

| Cache | Key Pattern | TTL | Eviction Trigger |
|---|---|---|---|
| Course list | `cache:courses:list:*` | 5 min | Course publish / lock |
| Course detail | `cache:course:<id>` | 5 min | Course publish / lock |
| Instructor analytics | `analytics:instructor:<courseId>:<instructorId>` | 60 s | Enrolment + completion events |
| Admin analytics | `analytics:admin:platform` | 60 s | Enrolment + completion events |
| AI reply | `aicache:<sha256(courseId:normalisedMessage)>` | 1 h | None (TTL only) |
| JWT blacklist | `blacklist:<token>` | 7 days | None (TTL matches token expiry) |
| Chat session | `chat:<sessionId>` | Configurable (`CHAT_SESSION_TTL_SECONDS`) | None (TTL) |

### Cache Implementation

- Spring Boot services use `spring-boot-starter-data-redis` with `@Cacheable` / `@CacheEvict` annotations
- AI service uses `redis.asyncio` for async cache lookups in the streaming path
- Cache hit for AI replies yields the full reply in a single `yield` — no Cerebras API call made

---

## Database

### Index Coverage

| Table | Index | Purpose |
|---|---|---|
| `courses` | `idx_courses_instructor_id` | Instructor dashboard queries |
| `enrolments` | `idx_enrolments_user_id`, `idx_enrolments_course_id` | Progress + completion checks |
| `lesson_progress` | `idx_lesson_progress_enrolment` | Progress fetch per enrolment |
| `certificates` | UK `(user_id, course_id)` | Deduplication + lookup |
| `idempotency_log` | PK `event_id` | O(1) duplicate check |
| `outbox_events` | `idx_outbox_published` | Relay polling (`published = false`) |

### Connection Pooling

- HikariCP with default pool size (10 connections per service)
- Outbox relay polls every 500 ms on a dedicated `@Scheduled` thread — does not contend with request threads

---

## Kafka Throughput

- Partition counts match the event bus spec: `course.published` — 3 partitions; all other topics — 6 partitions
- Outbox relay batches up to the HikariCP connection limit per poll cycle
- Consumer groups: each service has its own consumer group ID — no cross-service consumer contention

---

## AI Service

- Cerebras free tier: 30 RPM hard limit
- Redis reply cache reduces Cerebras calls for repeated or near-identical questions (same `courseId` + normalised message)
- Cache hit rate in demo conditions (2–3 learners asking similar questions): estimated 40–60%
- Streaming via Server-Sent Events (SSE) — first token appears in < 500 ms on cache miss

---

## Frontend

- Vite production build with code splitting per route (lazy imports not yet added — future optimisation)
- React Query / SWR not used; all fetches are manual `useEffect` calls with loading states
- Course list skeleton components prevent layout shift on load
- Certificate download: presigned S3 URL redirect — browser downloads directly from S3, zero load on cert-service

---

## Load Profile (Development / Demo)

| Scenario | Expected concurrency | Bottleneck |
|---|---|---|
| Live capstone demo | 1–5 users | None at this scale |
| AI chat (same question) | 2–3 users | Cache absorbs; < 1 Cerebras call/question |
| Course discovery | 1–5 users | Redis course-list cache (5 min TTL) |

---

*Last updated: 2026-05-15*
