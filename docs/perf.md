# LearnPulse — Performance Report
**Companion to:** `plan.md §13`

---

## Caching Strategy

### Redis Cache Layers

| Cache | Key Pattern | TTL | Eviction Trigger |
|---|---|---|---|
| Course list | `cache:courses:list:<queryHash>` | 5 min | Course published or locked |
| Course detail | `cache:courses:<courseId>` | 5 min | Course updated, published, or locked |
| Instructor analytics | `cache:analytics:instructor:<courseId>:<instructorId>` | 60 s | Enrolment, unenrolment, or lesson completion |
| Admin analytics | `cache:analytics:admin:platform` | 60 s | Enrolment, unenrolment, or lesson completion |
| AI reply | `aicache:<sha256(courseId:normalisedMessage)>` | 1 h | None (TTL only) |
| JWT blacklist | `blacklist:user:<userId>` | Remaining token lifetime (max 7 days) | Admin reinstates user |
| Chat session | `chat:<sessionId>` | Configurable (`CHAT_SESSION_TTL_SECONDS`) | None (TTL) |

### Cache Implementation

- Spring Boot services use `spring-boot-starter-data-redis` with `@Cacheable` / `@CacheEvict` annotations; key prefix is `"cache:" + cacheName + ":"` (configured in `CacheConfig`)
- AI service uses `redis.asyncio` for async cache lookups in the streaming path
- Cache hit for AI replies yields the full reply in a single `yield` — no Groq LLM call made
- Quiz attempt submission (`QuizAttemptService`) evicts both `analytics:instructor` and `analytics:admin` caches to keep completion rates accurate

---

## Database

### Index Coverage

| Table | Index | Purpose |
|---|---|---|
| `courses` | `idx_courses_instructor` on `(instructor_id)` | Instructor dashboard queries |
| `courses` | `idx_courses_status_vis` on `(status, visibility)` | Public course listing with status filter |
| `modules` | `idx_modules_course` on `(course_id)` | Module listing per course |
| `lessons` | `idx_lessons_module` on `(module_id)` | Lesson listing per module |
| `enrolments` | `idx_enrolments_course_status` on `(course_id, status)` | Completion counts per course |
| `enrolments` | `idx_enrolments_user_status` on `(user_id, status)` | Learner dashboard queries |
| `enrolments` | `idx_enrolments_analytics` on `(course_id, status, user_id, enrolled_at, completed_at)` | Covering index for instructor analytics — avoids table scan |
| `lesson_progress` | UK `(user_id, lesson_id)` | Completion deduplication + O(1) lookup |
| `lesson_progress` | `idx_lesson_progress_lesson_id` on `(lesson_id)` | Aggregate completion count per lesson |
| `quiz_attempts` | `idx_quiz_attempts_quiz_user` on `(quiz_id, user_id)` | Best-attempt lookup per learner |
| `course_generation_jobs` | `idx_gen_jobs_instructor` on `(instructor_id)` | Job history per instructor |
| `course_generation_jobs` | `idx_gen_jobs_status` on `(status)` | Polling for PENDING/FAILED jobs |
| `user_streaks` | UK `(user_id)` | O(1) streak upsert on lesson completion |
| `outbox_events` | `idx_outbox_status` on `(status, created_at)` | Relay polling for `PENDING` rows |
| `idempotency_log` | PK `event_id` | O(1) duplicate-event check |
| `certificates` | UK `(user_id, course_id)` | Exactly-once certificate guarantee |

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

- Groq free tier: ~30 RPM for `llama-3.3-70b-versatile`
- Redis reply cache reduces Groq calls for repeated or near-identical questions (same `courseId` + normalised message)
- Cache hit rate in demo conditions (2–3 learners asking similar questions): estimated 40–60%
- Streaming via Server-Sent Events (SSE) — first token appears in < 500 ms on cache miss
- AI Course Builder generation runs fully async via Kafka — the `POST /api/instructor/courses/generate` endpoint returns a `jobId` immediately; the pipeline (structure → content → quizzes) runs in the AI service and publishes `course.generation.completed` when done

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
| AI chat (same question) | 2–3 users | Cache absorbs; < 1 Groq call/question |
| Course discovery | 1–5 users | Redis course-list cache (5 min TTL) |
| AI course generation | 1 instructor | Async pipeline — no blocking HTTP; Groq RPM limit if multiple jobs run concurrently |

---

*Last updated: 2026-05-23*
