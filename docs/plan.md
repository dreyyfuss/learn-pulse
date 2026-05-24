# LearnPulse — Delivery Plan
**Version:** 2.0
**Companion to:** `PRD.md`, `schema.md`, `api-spec.md`, `kafka-events.md`
**Timeline:** May 5 – May 24 2026 · Feature work completed May 22

---

## Reading Guide

- Phases are sequential. Tasks within a phase can run in parallel.
- Each task lists an **estimate** (S = ≤ ½ day, M = 1–2 days, L = 3–5 days), **dependencies**, and an **acceptance check**.
- "DoD" (Definition of Done) at the end of each phase is the gate for moving on.

---

## Phase 0 — Foundations & Local Dev

**Goal:** The full stack can be cloned, started with a single command, and each service responds on its health endpoint. No business logic yet.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 0.1 | Finalise repo layout (`apps/course-service`, `apps/user-service`, `apps/cert-service`, `apps/web`, `apps/ai-service`, `infrastructure/`, `docs/`). | S | — | `tree -L 2` matches docs |
| 0.2 | Author `docker-compose.dev.yml`: MySQL 8 (three databases: `learnpulse_users`, `course_service_db`, `learnpulse_certs`), Kafka (KRaft), Kafka UI, MinIO (local S3), Redis 7, Traefik. Mailgun API is used directly in dev — no local SMTP container needed; ensure `MAILGUN_API_KEY` and `MAILGUN_DOMAIN` are set in `.env`. | M | 0.1 | `docker compose up` brings all services healthy |
| 0.3 | Bootstrap LMS Spring Boot 3 app (`apps/course-service`): `pom.xml`, `application.yml`, `HealthController`. Wire Flyway against `course_service_db`. | M | 0.1 | `GET /actuator/health` → 200 |
| 0.4 | Bootstrap React (Vite) app: routing skeleton with `/learn` and `/teach` namespaces. | M | 0.1 | Two empty dashboards render |
| 0.5 | Bootstrap FastAPI service: `requirements.txt`, `app/main.py`, `/healthz`. | S | 0.1 | `curl http://localhost:9000/healthz` → 200 |
| 0.6 | `infrastructure/kafka/topics.sh` — create all topics + DLQs per `kafka-events.md`. | S | 0.2 | `kafka-topics --list` shows all topics |
| 0.7 | GitHub Actions: lint + build for each app on PR. | M | 0.3, 0.4, 0.5, 0.12, 0.13 | Failing PR is blocked |
| 0.8 | Shared `.env.example` files for each app + root. | S | 0.1 | New dev can copy, fill, and run |
| 0.9 | GitHub Project board mirroring this plan. | S | — | Each task is tracked |
| 0.10 | `infrastructure/traefik/traefik.dev.yml` + Docker-label routing on each service container per `api-spec.md` §0.1: `/api/auth/*` → User Service `:8081`, `/api/learner/certificates` + `/api/certificates/*` → Cert Service `:8082`, `/api/*` → Course Service `:8080`, `/` → React SPA. Auth-endpoint rate-limit middleware (10 rpm, burst 5). | M | 0.2 | `curl http://localhost/api/actuator/health` → 200 via Traefik; 11 rapid login requests returns at least one `429` |
| 0.11 | Wire Redis 7 in `docker-compose.dev.yml` (port 6379, no auth in dev). Verify connectivity with `redis-cli ping`. | S | 0.2 | `PONG` response in local shell |
| 0.12 | Bootstrap User Service Spring Boot 3 app (`apps/user-service`): `pom.xml`, `application.yml` (datasource `learnpulse_users`), `HealthController`. Wire Flyway. | M | 0.1 | `GET /actuator/health` → 200 on `:8081` |
| 0.13 | Bootstrap Certificate Service Spring Boot 3 app (`apps/cert-service`): `pom.xml`, `application.yml` (datasource `learnpulse_certs`, S3 config, Kafka consumer config), `HealthController`. Wire Flyway. | M | 0.1 | `GET /actuator/health` → 200 on `:8082` |

**Phase 0 DoD:** Any team member can run the whole stack locally, hit health endpoints on all three backend services via Traefik on port 80, and `redis-cli ping` succeeds.

---

## Phase 1 — Auth & User Domain

> **Service context:** All backend tasks in this phase belong to the **User Service** (`apps/user-service`) and its own database (`learnpulse_users`). The User Service is the sole issuer of JWTs. Traefik's ForwardAuth middleware forwards every protected request to `GET /api/auth/validate`, which validates the token, checks the Redis blacklist, and returns `X-User-Id`, `X-User-Email`, and `X-User-Roles` headers. Downstream services read these headers — they never see raw tokens.

**Goal:** Users can register, log in, and the JWT/role machinery is in place. The frontend has a working login and protected-route guard.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 1.1 | Flyway migrations V1 (`users`, `user_roles`) per `schema.md`. | S | 0.12 | Tables created on startup in `learnpulse_users` |
| 1.2 | JPA entities + repositories: `User`, `UserRole`. | S | 1.1 | Unit test: save+load round-trip |
| 1.3 | `POST /api/auth/register` (learner + instructor variants). BCrypt cost 12. | M | 1.2 | Postman: register learner, register instructor → 201 |
| 1.4 | `POST /api/auth/login`, `POST /api/auth/refresh`. JWT carries `sub`, `email`, `roles`. | M | 1.3 | Login returns access + refresh tokens; bad password → 401 |
| 1.5 | Spring Security filter chain in User Service: JWT auth filter, role-based `@PreAuthorize` annotations, and `GET /api/auth/validate` for Traefik ForwardAuth. Configure ForwardAuth to call this endpoint on every non-public route. Course Service and Certificate Service wire a lightweight header-reading filter — no JWT dependency in downstream services. | M | 1.4 | Protected endpoint without token → 401 at Traefik; valid token → 200 on all three services |
| 1.6 | `GET /api/users/me`, `PATCH /api/users/me`. | S | 1.5 | Demo updating own profile |
| 1.7 | Admin endpoints: list users, promote, suspend, reinstate. Suspended users get 403 on next request (`api-spec.md` §3). | M | 1.5 | Suspending mid-session blocks subsequent calls |
| 1.8 | Seed first admin via `V2__seed_admin.sql` in the User Service (reads from env vars at startup). | S | 1.1 | Fresh DB has one admin |
| 1.9 | Frontend: login page, register page (with "Register as Instructor" toggle). | M | 1.4 | Manual flow works against local API |
| 1.10 | Frontend: auth context + protected route HOC. Token stored in `httpOnly` cookie or memory + refresh flow. | M | 1.9 | Page reload keeps user logged in until refresh expires |
| 1.11 | Frontend: `<RoleGuard>` component for route protection, role switcher in navbar (visible only to dual-role users). | M | 1.10 | Toggle navigates between `/learn/*` and `/teach/*` |
| 1.12 | Tests: unit on `JwtService`, integration on `/api/auth/*`. | S | 1.4 | CI green |
| 1.13 | Wire `spring-boot-starter-data-redis` (Lettuce) in the User Service. Configure `RedisTemplate` bean. All three backend services share the same Redis instance. | S | 0.11 | Integration test writes + reads a key |
| 1.14 | JWT blacklist in Redis: on `PATCH /api/admin/users/{id}/suspend`, write `blacklist:user:<id>` key (TTL = 7 days). The `/api/auth/validate` endpoint checks this key and returns 403 if present. On reinstate, delete the key. | M | 1.13, 1.7 | Suspend user mid-session; next API call to any service returns 403 within same JWT lifetime |

**Phase 1 DoD:** End-to-end demo — register a learner, register an instructor, log in, switch modes, hit a protected admin endpoint that 403s for non-admins. Suspend a logged-in user and confirm the immediate 403.

---

## Phase 2 — Course Authoring

> **Service context:** All backend tasks in this phase belong to the **Course Service** (`apps/course-service`) and its database (`course_service_db`).

**Goal:** Instructors can create courses, add modules and lessons, reorder them, and publish. Locking is wired up but not yet triggered (no learners have started anything).

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 2.1 | Flyway V1 (`courses`), V2 (`modules`, `lessons`, `lesson_attachments`) in Course Service. | S | 0.3 | Tables exist with constraints from `schema.md` |
| 2.2 | Entities + repositories for course graph. Eager-load by ID for the read view; lazy elsewhere. | M | 2.1 | Repository tests pass |
| 2.3 | `CourseService.create()` — auto-generate enrolment code for `PRIVATE`. | M | 2.2 | Private course → `enrolment_code` populated; public → null |
| 2.4 | Course REST endpoints (`api-spec.md` §4): create, list, get, update, list own. | M | 2.3 | Postman collection passes |
| 2.5 | Module + Lesson REST endpoints (`api-spec.md` §5). Reorder via `orderIndex`. | L | 2.4 | Cannot create lesson outside owned course; ordering preserved |
| 2.6 | `CourseLockGuard` aspect — any write to a `is_locked=1` course → `409 COURSE_LOCKED`. | M | 2.5 | Manually flip flag in DB; updates fail with structured error |
| 2.7 | `POST /api/courses/{id}/publish` — validates ≥ 1 module + each module ≥ 1 lesson. Stub event emit for now. | M | 2.5 | Empty course → 422; valid course → 200 |
| 2.8 | Admin `DELETE /api/courses/{id}` — cascades. | S | 2.4 | Delete course; modules/lessons gone |
| 2.9 | Frontend: instructor "My Courses" page (`/teach/courses`). | M | 2.4 | Lists owned courses with status |
| 2.10 | Frontend: course editor (modules + lessons) — drag-and-drop reorder. | L | 2.5 | Reorder persists on save |
| 2.11 | Frontend: publish button + validation messages (422 surfaced inline). | S | 2.7 | Empty course shows "needs at least one lesson" |
| 2.12 | Public course list page (`/learn/browse`) — read-only for now. | M | 2.4 | Only `PUBLISHED` courses appear; `enrolment_code` not in payload |
| 2.13 | `@Cacheable` on `GET /api/courses` and `GET /api/courses/{id}` using Spring Cache → Redis (TTL 5 min). `@CacheEvict` on publish, update, delete. | M | 1.13, 2.4 | Second call hits Redis; publish evicts cache |

**Phase 2 DoD:** Instructor can build a 3-module / 6-lesson course end-to-end and publish it. Learner can browse it but not yet enrol. Course list responses are cached in Redis.

---

## Phase 3 — Content Upload & Display

> **Service context:** Backend changes in `apps/course-service`. Frontend in `apps/web`. Object storage via MinIO (dev) / S3 (prod).

**Goal:** Instructors can upload video, document, and Markdown article files directly through the Course Builder. Learners see a type-appropriate viewer (HTML5 player, PDF iframe, Markdown renderer). Legacy lessons with a `content_url` continue to work via fallback.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 3.1 | Implement `S3Config` bean in course-service (mirrors cert-service; adds `public-endpoint` override for presigned URLs so browsers can reach MinIO directly). | S | 0.2 | `S3Client` + `S3Presigner` beans initialise on startup |
| 3.2 | Implement `StorageService` in course-service: `presignUploadUrl`, `presignDownloadUrl`, `delete`. | S | 3.1 | Manual test: generate presigned PUT URL, upload a file, presigned GET URL returns it |
| 3.3 | Flyway `V9__add_content_key.sql`: add `content_key` to `lessons`, `s3_key` to `lesson_attachments`, make `s3_url` nullable. Update `Lesson` and `LessonAttachment` JPA entities. | S | — | Migrations apply cleanly on fresh DB |
| 3.4 | Create `LessonContentService` and `LessonContentController` (7 endpoints: content upload-url, confirm, GET, DELETE; attachment upload-url, confirm, GET download-url). | M | 3.1, 3.2, 3.3 | Postman: full upload → confirm → GET flow for each content type |
| 3.5 | `docker-compose.dev.yml`: add `APP_S3_ACCESS_KEY/SECRET_KEY/PUBLIC_ENDPOINT` to course-service; update `minio-init` to configure bucket CORS for browser direct-upload. | S | 3.1 | MinIO PUT from browser succeeds without CORS error |
| 3.6 | Frontend: add 7 content API methods to `courseService.js`. | S | 3.4 | Methods callable from browser console without errors |
| 3.7 | Frontend: build `LessonContentUpload` component (file picker for VIDEO/DOCUMENT, Markdown textarea for ARTICLE, progress bar, two-step upload flow). | M | 3.6 | Instructor uploads a PDF and sees "Uploaded successfully" |
| 3.8 | Frontend: build `LessonContentViewer` component (HTML5 video, PDF iframe + download, react-markdown renderer, legacy fallback). | M | 3.6 | Learner sees video playing / PDF rendered / Markdown text |
| 3.9 | Wire `LessonContentUpload` into `CourseBuilder.jsx` (replace Content URL input). Wire `LessonContentViewer` into `CoursePlayer.jsx`. | S | 3.7, 3.8 | End-to-end: upload video in builder → learner sees player in player view |

**Phase 3 DoD:**
- Instructor uploads a video, a PDF document, and a Markdown article through the Course Builder.
- Enrolled learner sees the correct viewer for each type in the Course Player.
- Legacy lessons with `content_url` (and no `content_key`) still render via the fallback URL.
- An instructor can upload a supplementary attachment; an enrolled learner can download it.

---

## Phase 4 — Enrolment, Progression & Locking

**Goal:** Learners enrol (public + private), start a course (which locks it), complete lessons sequentially, and unlock modules. No certificates yet.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 4.1 | Flyway V3 (`enrolments`, `lesson_progress`, `module_unlocks`) in Course Service. | S | 2.1 | Tables + constraints in place |
| 4.2 | Entities + repositories for enrolment domain. | S | 4.1 | Repository tests pass |
| 4.3 | `POST /api/enrolments` — public + private (with code) flows. Unique `(user_id, course_id)`. | M | 4.2 | Duplicate enrolment → 409 |
| 4.4 | `POST /api/enrolments/{id}/start` — sets `started_at`, locks the course (atomic), seeds `module_unlocks` for module 1. Idempotent. | M | 4.3, 2.6 | Calling twice returns same `startedAt` |
| 4.5 | `POST /api/lessons/{id}/complete` — full validation chain (`api-spec.md`): enrolment exists, module unlocked, prereqs done. Out-of-order → 409. | L | 4.4 | Skipping a lesson returns `LESSON_OUT_OF_ORDER` |
| 4.6 | Module-unlock side effect: when last lesson in a module completes, insert next `module_unlocks` row OR mark enrolment `COMPLETED` if final module. | M | 4.5 | DB trace shows correct state transitions |
| 4.7 | `GET /api/enrolments/{id}/progress` — full tree with `completed`/`unlocked` flags. | M | 4.5 | Progress matches DB state |
| 4.8 | `GET /api/learner/enrolments` — list summary. | S | 4.3 | Lists with progress percentages |
| 4.9 | Admin enrol/unenrol endpoints. | S | 4.3 | Admin can manually enrol any user |
| 4.10 | Frontend: course detail page with "Enrol" / "Request Access" CTA + private-code modal. | M | 4.3 | Wrong code shows error in modal |
| 4.11 | Frontend: learner dashboard (`/learn/courses`) with "Start Course" button. Confirmation dialog ("This will lock the course for editing"). | M | 4.4 | Shows `startedAt` after click |
| 4.12 | Frontend: course player UI — sequential lesson list, lock icons on future modules, "Mark complete" button. | L | 4.5, 4.6, 4.7 | Cannot complete future lessons; UI unlocks next module instantly |
| 4.13 | Frontend: instructor view becomes read-only for locked courses (banner explains why). | S | 4.4 | Edit buttons hidden / disabled |

**Phase 4 DoD:** A learner enrols, starts, and completes a multi-module course; instructor sees the course locked; analytics not yet wired.

---

## Phase 5 — Quizzes

> **Service context:** All backend changes in `apps/course-service`. Frontend changes in `apps/web`. Quizzes are first-class module items alongside lessons: they participate in module-completion gating and can be drag-reordered with lessons.

**Goal:** Instructors can create quizzes with multiple-choice and true/false questions and set a passing score. Completing all lessons in a module is not enough to unlock the next — learners must also pass the module's quiz.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 5.1 | Flyway `V10__add_quiz_tables.sql`: `quizzes`, `quiz_questions`, `quiz_options`, `quiz_attempts`. | S | 4.1 | Tables created on fresh DB startup |
| 5.2 | JPA entities: `Quiz`, `QuizQuestion`, `QuizOption`, `QuizAttempt`. Repositories: `QuizRepository`, `QuizAttemptRepository`. | M | 5.1 | Repository save+load round-trip tests pass |
| 5.3 | Refactor `ModuleProgressChecker`: replace inline lesson-only completion logic with a shared service that gates on both all lessons done **and** all quizzes passed (best attempt ≥ passing score). Used by both `LessonProgressService` and `QuizAttemptService`. | M | 5.2, 4.6 | Completing all lessons with an unpassed quiz does NOT unlock next module; passing quiz with all lessons done DOES unlock it |
| 5.4 | Quiz instructor CRUD endpoints (`api-spec.md`): `POST`, `GET`, `PATCH`, `DELETE` under `/api/courses/{courseId}/modules/{moduleId}/quizzes`. `PUT /{quizId}/questions` replaces all questions atomically. Guarded by course ownership + lock guard. | M | 5.2 | Postman: create quiz with questions; update title; delete |
| 5.5 | Quiz player endpoint `GET /api/quizzes/{quizId}/player` (learner-facing): verifies active enrolment + module unlock; returns questions with options but **without** `isCorrect` flags. | S | 5.2, 4.4 | Non-enrolled learner → 404; locked module → 404; enrolled + unlocked → options returned without correct flag |
| 5.6 | Quiz attempt submission `POST /api/quizzes/{quizId}/attempts`: scores answers against correct options, computes percentage score, evaluates pass/fail vs `passing_score`, saves `QuizAttempt`, runs `ModuleProgressChecker`, returns per-question feedback with `correctOptionId`. | M | 5.3, 5.5 | Score 0–100 computed correctly; passing triggers module unlock side-effect |
| 5.7 | Best attempt retrieval `GET /api/quizzes/{quizId}/attempts/best`: returns highest-scoring attempt for the caller, or null data if none. | S | 5.6 | No attempt → 200 with null data; after attempts → returns highest score |
| 5.8 | Quiz reorder endpoint `PUT /api/courses/{courseId}/modules/{moduleId}/quizzes/reorder`. | S | 5.2 | Reorder two quizzes; DB reflects new `orderIndex` values |
| 5.9 | Frontend `QuizEditor` component: edit quiz metadata (title, passing score) and questions (add/remove question, add/remove option, mark correct, set question type). Wired into `CourseBuilder.jsx`. | L | 5.4 | Instructor adds a TRUE_FALSE question with two options, saves, sees it reflected |
| 5.10 | Refactor `CourseBuilder.jsx` DnD: replace lesson-only drag ref with unified `dragItem` ref across the merged lessons+quizzes array. Drop handler reassigns `orderIndex` 0…N, splits back into lessons/quizzes, and fires both reorder calls in parallel. | M | 5.8, 5.9 | Dragging a quiz above a lesson persists after page reload; module drag still works |
| 5.11 | Tests: `ModuleProgressCheckerTest`, `QuizServiceTest`, `QuizAttemptServiceTest`, `QuizControllerTest`, `QuizAttemptControllerTest`. Fix `LessonProgressServiceTest` to mock `ModuleProgressChecker`. | M | 5.3–5.8 | `./mvnw test` green |

**Phase 5 DoD:**
- Instructor creates a module with lessons and quizzes, sets a passing score, and reorders items via drag-and-drop.
- Learner completes all lessons but fails the quiz → next module stays locked.
- Learner passes the quiz → next module unlocks (or course completes if final module).
- Per-question feedback (correct/incorrect, correct option revealed) returned on submission.
- All backend tests green.

---

## Phase 6 — Kafka Backbone & Email Pipeline

**Goal:** Real Kafka events replace the stubs from Phase 2. Mailgun sends welcome and module-unlocked emails. Idempotency is proven by tests.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 6.1 | Kafka producer config in Spring (`acks=all`, `enable.idempotence=true`). | S | 0.6 | Producer bean wired with idempotent settings |
| 6.2 | Flyway V4 (`idempotency_log`, `outbox_events`) in Course Service. | S | 4.1 | Tables created |
| 6.3 | Outbox table + `OutboxPublisher` scheduled job (`kafka-events.md`). | M | 6.1 | DB row → Kafka topic within 1s |
| 6.4 | Replace `course.published` stub with real event emission via outbox. Schema per `kafka-events.md`. | S | 6.3, 2.7 | Publish a course; consumer logs the event |
| 6.5 | Emit `user.enrolled` from enrolment service via outbox. | S | 6.3, 4.3 | Enrolling produces event |
| 6.6 | Emit `module.unlocked` and `course.completed` from progress service. Final-module rule: emit `course.completed`, NOT `module.unlocked`. | M | 6.3, 4.6 | Final lesson → only `course.completed` |
| 6.7 | `EmailConsumer` in User Service (Kafka group `email-service`): handle `user.enrolled` and `module.unlocked`. Mailgun API. Idempotency-log check. | L | 6.5, 6.6 | Welcome email delivered via Mailgun in dev |
| 6.8 | Integration test with `EmbeddedKafka`: duplicate `eventId` → single email sent. | M | 6.7 | Test passes deterministically |
| 6.9 | DLQ wiring + dashboard panel (Kafka UI). | S | 6.7 | Manually poisoned message lands in `*.dlq` |

**Phase 6 DoD:** Enrolling and completing modules triggers real emails through the Kafka pipeline; idempotency tests are green.

---

## Phase 7 — Certificate Generation

> **Service context:** This phase builds the **Certificate Service** (`apps/cert-service`) — a standalone Spring Boot application with its own database (`learnpulse_certs`). It consumes `course.completed` from Kafka, generates the PDF, and exposes the certificate endpoints. Email delivery lives in the **User Service** via Kafka.

**Goal:** Course completion produces a PDF certificate, stores it in S3 (MinIO locally), inserts the certificate row in the **same DB transaction** as `idempotency_log`, and emails the learner.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 7.1 | S3 client wrapper (`software.amazon.awssdk` v2) in Certificate Service. Configurable endpoint for MinIO in dev. | S | 0.13 | Smoke test uploads a file to MinIO |
| 7.2 | Thymeleaf certificate template + Flying Saucer renderer. Template includes: learner name, course, instructor, date, cert UUID, logo. The Certificate Service calls User Service and Course Service REST APIs to fetch learner and course details. | M | 7.1 | Render & open a sample PDF |
| 7.3 | `CertificateConsumer` in Certificate Service (group `certificate-service`) consuming `course.completed`. Implements the exactly-once flow from `kafka-events.md`. Flyway V1 (`certificates`, `idempotency_log`) runs on startup. | L | 6.6, 7.2 | Single message → single row in `certificates` in `learnpulse_certs` |
| 7.4 | After successful commit in Certificate Service, emit `certificate.generated` via outbox. | S | 7.3, 6.3 | Topic receives event |
| 7.5 | Extend `EmailConsumer` (User Service) to handle `certificate.generated`. | S | 6.7, 7.4 | Mailgun delivers email with download link |
| 7.6 | `GET /api/learner/certificates` and `GET /api/certificates/{id}/download` (signed S3 URL, 5 min TTL) — served by Certificate Service; Traefik routes these paths automatically. | M | 7.3 | Click link → PDF downloads |
| 7.7 | Concurrency test: two consumer threads receive the same `eventId` simultaneously → exactly one row in `certificates`, one in `idempotency_log`. | M | 7.3 | Test passes ≥ 100 iterations |
| 7.8 | Frontend: "My Certificates" page (`/learn/certificates`). | M | 7.6 | Lists and downloads |
| 7.9 | Frontend: completion celebration screen at end of final lesson. Polls `/api/learner/certificates` until cert appears. | M | 7.6 | Cert appears within 30s |

**Phase 7 DoD:** Completing a course produces exactly one PDF, stored in S3, sent by email, downloadable from the dashboard. Concurrency test green.

---

## Phase 8 — AI Study Assistant

**Goal:** A learner inside a started course can chat with a per-course assistant whose answers are grounded in that course's lessons only.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 8.1 | FastAPI app structure: `app/main.py`, `app/kafka/consumer.py`, `app/rag/`, `app/api/`. | S | 0.5 | Skeleton boots |
| 8.2 | `aiokafka` consumer for `course.published`. Uses group `ai-service-indexer`. | M | 6.4, 8.1 | Receiving a published course logs payload |
| 8.3 | Embedding + chunking pipeline. Embedding model: `sentence-transformers/all-MiniLM-L6-v2` (local). | M | 8.2 | Lessons are chunked + embedded |
| 8.4 | ChromaDB integration: persistent local store at `./chroma_data`. Namespace by `course_id`. | M | 8.3 | Inspecting Chroma shows N vectors per course |
| 8.5 | `POST /ai/courses/{courseId}/chat` endpoint with `userId`, `message`, `chatHistory`. | M | 8.4 | Returns reply + sources |
| 8.6 | LangChain RAG chain wired to `langchain-groq` (`ChatGroq`, model `llama-3.3-70b-versatile`). System prompt restricts answers to retrieved chunks. | M | 8.5 | Out-of-scope question → polite refusal |
| 8.7 | Service-to-service shared secret (`X-Service-Auth`) verified by FastAPI. | S | 8.5 | Wrong secret → 401 |
| 8.8 | Spring Boot proxy `POST /api/courses/{courseId}/ai/chat` — verifies enrolment + `started_at`, then forwards. | M | 8.5, 4.4 | Non-enrolled user → 403 |
| 8.9 | Frontend: chat panel inside the course player. | L | 8.8 | Learner asks "What is REST?" → grounded answer with sources |
| 8.10 | Tests: deterministic smoke test that publishes a fixture course event and asserts a known question retrieves the right lesson. | M | 8.6 | CI green |

**Phase 8 DoD:** Live demo — instructor publishes a 3-lesson course, learner enrols, starts, asks 3 questions, AI cites the correct lessons.

---

## Phase 9 — AI Course Builder

> **Service context:** New Kafka topics wire the Course Service to the AI Service. The AI Service adds a generation pipeline separate from the existing RAG chat pipeline. The Course Service gets a new `CourseGenerationJob` entity and two instructor endpoints. The frontend gets a modal-driven prompt-to-course flow.

**Goal:** Instructors can enter a text prompt and have a complete DRAFT course — modules, Markdown lesson content, and per-lesson quizzes — generated automatically. Generation runs asynchronously over Kafka; the frontend polls for completion.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 9.1 | `infrastructure/kafka/topics.sh`: add `course.generation.requested`, `course.generation.completed`, `course.generation.failed` + DLQs. | S | 0.6 | `kafka-topics --list` shows 6 new topics |
| 9.2 | Flyway `V11__add_generation_job.sql`: `course_generation_jobs` table; `ALTER TABLE lessons ADD COLUMN generated_content MEDIUMTEXT NULL`. | S | 2.1 | Tables/column exist on fresh DB |
| 9.3 | `CourseGenerationJob` JPA entity (UUID PK); `JobStatus` enum (PENDING / COMPLETED / FAILED); `CourseGenerationJobRepository`. | S | 9.2 | Repository save+load round-trip passes |
| 9.4 | Event record DTOs: `CourseGenerationRequestedEvent`, `CourseGenerationCompletedEvent` (nested generated module/lesson/quiz/question/option records), `CourseGenerationFailedEvent`. `CourseGenerationProducer` emits `course.generation.requested` via outbox. | M | 9.3 | Publishing a job logs the emitted event |
| 9.5 | Dedicated `aiResultsListenerContainerFactory` (manual-ack, `AckMode.MANUAL_IMMEDIATE`) for the two result topics. | S | 0.6 | Bean wires without startup errors |
| 9.6 | `CourseGenerationConsumer` in course-service: `@KafkaListener` on `course.generation.completed` + `course.generation.failed`; manual ack; dispatches to `CourseGenerationService`. | S | 9.4, 9.5 | Consuming a fixture event calls the correct service method |
| 9.7 | `CourseGenerationService`: `initiate()` — creates PENDING job, fires `requested` event, returns job response. `handleCompleted()` — builds full Course/Module/Lesson/Quiz graph, uploads Markdown to S3 as `lessons/{id}/content.md`, sets `content_key`, marks job COMPLETED. `handleFailed()` — marks job FAILED with `errorMessage`. | L | 9.3, 9.4 | End-to-end: fixture completed event → course row + S3 objects + job COMPLETED |
| 9.8 | Two new instructor endpoints: `POST /api/instructor/courses/generate` → 202 with job response; `GET /api/instructor/courses/generate/{jobId}` → current status + `courseId` when done. | S | 9.7 | Postman: POST returns jobId; GET returns PENDING then COMPLETED |
| 9.9 | AI service Pydantic schemas (`app/schemas/generation.py`): `CourseGenerationRequestedEvent`, `CourseOutline`, `ModuleOutline`, `LessonOutline`, `GeneratedQuiz`, `QuizQuestion`, `QuizOption`. | S | — | Schemas importable; `model_validate` round-trip test passes |
| 9.10 | `CourseGenerationPipeline` (`app/generation/pipeline.py`): Step 1 — LLM call (`ChatGroq`) to produce a `CourseOutline` (3–5 modules × 3–5 lessons). Step 2 — parallel `llm.abatch()` for Markdown lesson content and quiz JSON for every lesson simultaneously. Assembles completed-event payload. | L | 9.9 | Pipeline called with a fixture prompt returns a valid completed payload with content + quizzes |
| 9.11 | `CourseGenerationConsumer` (aiokafka): subscribes to `course.generation.requested`, runs `CourseGenerationPipeline`, emits completed or failed. Manual offset commit after successful publish. | M | 9.10 | Sending a Kafka message triggers pipeline; completed event appears in topic |
| 9.12 | `GenerationEventProducer`: `publish_completed(payload)` → `course.generation.completed`; `publish_failed(jobId, instructorId, reason)` → `course.generation.failed`. | S | 9.11 | Topics receive correct JSON payloads |
| 9.13 | Frontend: `AiGenerateModal` component — prompt textarea (10–2000 char validation), animated status messages, polls `getGenerationJob` every 3 s up to 180 s; on COMPLETED calls `onSuccess(courseId)`; on FAILED shows `errorMessage`. | M | 9.8 | Submitting a prompt shows progress UI; COMPLETED auto-navigates to CourseBuilder; FAILED shows error inline |
| 9.14 | `MyCourses.jsx`: "Generate with AI" button opens `AiGenerateModal`; `onSuccess` navigates to `/teach/courses/{courseId}/edit`. | S | 9.13 | Button visible in My Courses header; success navigates to editor |
| 9.15 | `courseService.js`: `generateCourse(body)` → `POST /api/instructor/courses/generate`; `getGenerationJob(jobId)` → `GET /api/instructor/courses/generate/{jobId}`. | S | 9.8 | Both methods callable from browser console without errors |
| 9.16 | `CourseBuilder.jsx`: lesson content panel reads `lesson.generatedContent` as the initial Markdown value. | S | 9.13 | Opening a generated course shows lesson content pre-filled in the editor |

**Phase 9 DoD:**
- Instructor clicks "Generate with AI", enters a description, and within ~3 minutes a complete DRAFT course (3–5 modules, 3–5 lessons each, per-lesson Markdown content + quiz) appears in the CourseBuilder.
- Frontend shows animated progress messages and polls every 3 s; navigates automatically on completion.
- Generated Markdown is uploaded to S3 and surfaced as an ARTICLE lesson with the existing `LessonContentViewer`.
- FAILED jobs surface the error message inline in the modal.

---

## Phase 10 — Learning Streaks

> **Service context:** All backend changes in `apps/course-service`. Frontend changes in `apps/web`. Streak state is stored per-user in the `user_streaks` table and updated whenever a learner completes a lesson or submits a quiz attempt.

**Goal:** Learners are motivated to learn every day by a streak counter that tracks consecutive days of activity. Shown as a badge on the learner dashboard and a persistent chip in the navbar.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 10.1 | Flyway `V12__add_user_streaks.sql`: `user_streaks` table (`id` BINARY(16) PK, `user_id` BINARY(16) UNIQUE, `current_streak INT`, `longest_streak INT`, `last_activity_date DATE`, `updated_at TIMESTAMP`). | S | 4.1 | Table created with unique constraint on `user_id` |
| 10.2 | `UserStreak` JPA entity (UUID PK); `UserStreakRepository` with `findByUserId`. | S | 10.1 | Repository save+load round-trip passes |
| 10.3 | `StreakService.recordActivity(UUID userId)`: idempotent same-day check; increments on consecutive day; resets to 1 on gap > 1 day; updates `longestStreak`. `getStreak()` returns `StreakResponse(currentStreak, lastActivityDate)` or zeros if no record. All date comparisons use `LocalDate.now(ZoneOffset.UTC)`. | M | 10.2 | Unit tests: new user, same-day idempotent, consecutive increment, longest streak update, missed-day reset |
| 10.4 | `StreakResponse` record DTO: `int currentStreak`, `LocalDate lastActivityDate`. | S | 10.3 | Serialises to JSON correctly |
| 10.5 | `GET /api/learner/streak` in `StreakController`, `@PreAuthorize("hasRole('LEARNER')")`. Returns `ApiResponse<StreakResponse>`. | S | 10.3, 10.4 | Unauthenticated → 401; INSTRUCTOR role → 403; LEARNER with no activity → `{ currentStreak: 0, lastActivityDate: null }` |
| 10.6 | Inject `StreakService` into `LessonProgressService`: call `recordActivity(userId)` after save on the new completion path only. | S | 10.3, 4.5 | Completing a lesson once → streak increments; calling complete again same lesson → streak unchanged |
| 10.7 | Inject `StreakService` into `QuizAttemptService`: call `recordActivity(userId)` after every attempt save. | S | 10.3, 5.6 | Submitting a quiz attempt → streak updated; same-day second attempt → idempotent |
| 10.8 | Tests: `StreakServiceTest`, `StreakControllerTest`. Update `LessonProgressServiceTest` and `QuizAttemptServiceTest` to mock `StreakService`. | M | 10.5, 10.6, 10.7 | `./mvnw test` green |
| 10.9 | Frontend `streakService.js`: `getMine()` → `GET /api/learner/streak`. | S | 10.5 | Method callable from browser console without errors |
| 10.10 | Frontend `streakStore.js` (Zustand): `streak`, `setStreak`. `getStreakState(streak)` helper returns `'done'` (activity today), `'at-risk'` (activity yesterday, not yet today), or `'none'`. | S | 10.9 | `getStreakState` returns correct state for each scenario |
| 10.11 | `LearnDashboard.jsx`: include `streakService.getMine()` in startup `Promise.all`; render a three-state streak badge (coral/checkmark done, amber/at-risk, hidden none). | M | 10.10 | Dashboard shows coral badge after lesson completion; amber badge the following day before activity |
| 10.12 | `Navbar.jsx`: compact flame-icon chip in learner mode, reading from `useStreakStore`. Same colour states. Hidden when streak is none. | S | 10.10 | Streak chip appears in navbar after any learning activity; updates without page reload |

**Phase 10 DoD:**
- Completing a lesson or submitting a quiz attempt updates the learner's streak.
- Same-day repeat actions are idempotent; a one-day gap increments the streak; a gap > 1 day resets it to 1.
- `GET /api/learner/streak` returns current streak and last activity date; role-guarded.
- Dashboard and navbar reflect streak state with correct colour coding.
- All backend tests green.

---

## Phase 11 — Analytics & Polish

**Goal:** Instructors and admins get actionable dashboards. UX rough edges sanded down. Project demo-ready.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 11.1 | `GET /api/instructor/courses/{id}/analytics` — aggregate + per-learner. SQL is read-heavy; add indexes per `schema.md`. | M | 4.7 | Numbers match a hand-counted fixture |
| 11.2 | `GET /api/admin/analytics` — platform-wide. | S | 11.1 | Admin dashboard populated |
| 11.3 | Frontend: instructor analytics page — charts for completion rate, per-learner table. | L | 11.1 | Sortable table, completion donut |
| 11.4 | Frontend: admin dashboard — users / courses / enrolments tabs with admin actions (promote, suspend, delete course, manual enrol). | L | 1.7, 2.8, 4.9 | All admin flows clickable |
| 11.5 | Empty states + error toasts across the app. Map all `error.code` values to friendly messages. | M | All FE | Triggering each error code shows readable copy |
| 11.6 | Loading skeletons and 404 page. | S | All FE | Slow network demo still feels OK |
| 11.7 | Verify Traefik rate-limit middleware from Phase 0.10 is effective. Tune `burst` and `average` values if needed. | S | 0.10 | k6 hammering `/api/auth/login` → 429 responses from Traefik |
| 11.8 | Logging + `traceId` propagation across Spring Boot ↔ Kafka ↔ FastAPI. | M | All BE | A single learner-action trace can be reconstructed |
| 11.9 | README walkthrough + architecture diagram. Must cover the three-service layout and Traefik routing. | S | — | Fresh dev follows README and demos in 15 min |
| 11.10 | `@Cacheable` on analytics endpoints (60 s TTL). Evict on enrolment + completion events. | S | 11.1, 1.13 | Analytics endpoint returns Redis-cached data on repeat call; stale after 60 s max |
| 11.11 | AI reply cache in Redis (FastAPI + `redis.asyncio`): key = `sha256(courseId + normalise(message))`, TTL 1 hour. On cache hit, skip Groq call entirely. | S | 8.6, 0.11 | Repeated identical question returns instantly; Groq RPM counter unchanged |

**Phase 11 DoD:** Capstone-quality demo. Instructor and admin dashboards complete. Logs are searchable. Caching and rate-limiting validated. README onboards a new contributor.

---

## Phase 12 — App Monitoring

> **Service context:** Adds a metrics pipeline across all four services. No new business logic — purely observability infrastructure.

**Goal:** All services expose Prometheus metrics. Prometheus scrapes them every 15 s. Grafana auto-provisions a **LearnPulse Overview** dashboard on every `docker compose up`.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 12.1 | Add `micrometer-registry-prometheus` dependency to all three Spring Boot services. | S | 0.3, 0.12, 0.13 | Dependency resolves in Maven build |
| 12.2 | Expose `prometheus` actuator endpoint in each Spring Boot `application.yml`; add `management.metrics.tags.application` for per-service labelling. | S | 12.1 | `GET /actuator/prometheus` on each service returns Prometheus text format |
| 12.3 | Add `prometheus-fastapi-instrumentator` to AI service `requirements.txt`; wire it in `app/main.py`. | S | 0.5 | `GET http://localhost:9000/metrics` returns Prometheus text format |
| 12.4 | Create `infrastructure/monitoring/prometheus.yml` — 15 s scrape interval; one job per service. | S | 12.2, 12.3 | Prometheus UI shows all targets `State = UP` |
| 12.5 | Create Grafana provisioning tree under `infrastructure/monitoring/grafana/provisioning/`: datasource pointing at Prometheus; `learnpulse.json` dashboard with three rows — JVM, HTTP, AI Service. | M | 12.4 | On fresh `docker compose up`, Grafana → **LearnPulse Overview** loads with live data |
| 12.6 | Add `prometheus` and `grafana` services to `docker-compose.dev.yml`; add named volumes. | S | 12.4, 12.5 | http://localhost:3000 reachable; http://localhost:9090 reachable |

**Phase 12 DoD:** `docker compose up` brings Prometheus and Grafana. All `/actuator/prometheus` and `/metrics` endpoints return scrape data. The provisioned dashboard displays live metrics without any manual configuration.

---

## Phase 13 — Hardening & Submission

**Goal:** Bug bash, documentation, deployment dry-run. Submitted by May 24.

| # | Task | Est. | Depends on | Acceptance |
|---|---|---|---|---|
| 13.1 | Whole-team bug bash — structured: each tester gets a persona (admin, instructor, learner). | S | Phase 11 | Issues filed and triaged |
| 13.2 | Performance check: p95 latency on key endpoints under 50 concurrent users (k6 script). Targets per PRD. | M | Phase 11 | Report committed to `docs/perf.md` |
| 13.3 | Security check: OWASP top-10 sweep, JWT secret rotation, S3 bucket policy review, no enrolment codes in public payloads, Redis `requirepass` set in staging, Traefik dashboard disabled in prod, `actuator` endpoints IP-whitelisted. | M | Phase 11 | Checklist in `docs/security.md` |
| 13.4 | Production-style deploy dry-run: `infrastructure/traefik/traefik.prod.yml` with TLS. Confirm Traefik → all three Spring Boot services → FastAPI chain works end-to-end. | L | 13.3 | Live URL accessible; all three services reachable via correct path prefixes |
| 13.5 | Demo script + recording. | M | 13.4 | 10-minute video walks the user journey |
| 13.6 | Capstone submission package: link to repo, deployed URL, demo video, `plan.md` + PRD. | S | 13.5 | Submitted by May 24 |

**Phase 13 DoD:** Capstone delivered.

---

## Cross-Cutting Tracks

These don't fit a single phase — pull throughout the project.

| Track | Notes |
|---|---|
| **Tests** | Aim for: ≥ 80 % service-layer coverage on backend; one happy-path E2E per Phase DoD. |
| **CI/CD** | Lint → build → test → docker image. Branch protection on `main`. |
| **Observability** | Structured JSON logs, `traceId` per request, Kafka UI for topic depth. Redis `INFO stats` tracked in the Phase 13 perf report. Prometheus + Grafana (Phase 12). |
| **Traefik config** | Two configs: `traefik.dev.yml` (no TLS, dashboard on `:8090`) and `traefik.prod.yml` (TLS, dashboard disabled). Both under `infrastructure/traefik/`. |
| **Documentation** | Update the relevant doc when a behaviour changes. PRs that change an API also touch `api-spec.md`. |

---

## Risk Register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Kafka exactly-once subtleties | High | Land Phase 6 early; write the concurrency test (7.7) the day the consumer is born. |
| Groq free-tier rate limits during demo | Medium | Redis AI reply cache (task 11.11) absorbs repeated questions. Fallback to a stock "I'm at capacity" message on 429 from Groq. |
| Course locking corner cases (race between `start` and `update`) | Medium | Use a DB-level row lock in `start()`; cover with an integration test. |
| S3 / MinIO config drift between dev and staging | Medium | One `S3Client` wrapper; only the endpoint differs via env var. |
| Redis unavailability | Low | Spring Cache `@Cacheable` is non-critical — errors fall back to the DB. JWT blacklist failure is a security risk; document a circuit-breaker fallback that re-checks `users.status` in DB if Redis is unreachable. |
| Traefik misconfiguration silently breaking SPA routing | Low | Add an E2E smoke test that hits a deep-link URL through Traefik and confirms 200 + React root HTML. |
| Schema breaking changes after launch | Low (capstone scope) | Topic versioning convention in `kafka-events.md`. |

---

## Definition of Done — Project Level

A feature is *done* when:
1. Code merged to `main` via reviewed PR.
2. Migrations applied automatically on startup.
3. Tests cover the happy path **and** the documented error path.
4. The relevant doc (`api-spec.md` / `kafka-events.md` / `schema.md`) is updated in the same PR if behaviour changed.
5. The user-visible flow is demoable in the local stack with no manual DB poking.

---

*End of Document*
