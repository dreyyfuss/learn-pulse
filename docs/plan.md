# LearnPulse — Delivery Plan
**Version:** 1.0
**Companion to:** `PRD.md`, `ERD.md`, `api-spec.md`, `kafka-events.md`
**Owner:** Capstone team
**Cadence:** Weekly demo at end of each phase

---

## Reading Guide

- **Phases run sequentially**, but tasks within a phase can be parallelised across team members.
- Each task lists: **Owner role** (Backend / Frontend / AI / DevOps / Full-stack), **estimate** (S = ≤ ½ day, M = 1–2 days, L = 3–5 days), **dependencies**, and an **acceptance check** the team can demo.
- Suggested team split (6 people): 2 backend, 2 frontend, 1 AI/Python, 1 DevOps. Adjust labels if your split differs — the work doesn't change.
- "DoD" (Definition of Done) at the end of each phase is the gate for moving on.

### Suggested team labels
- **BE-A / BE-B** — Spring Boot backend pair
- **FE-A / FE-B** — React frontend pair
- **AI** — FastAPI / LangChain / RAG
- **DEVOPS** — Docker, Traefik, Kafka, MySQL, Redis, S3, CI

---

## Phase 0 — Foundations & Local Dev (Week 1)

**Goal:** Every developer can clone the repo, run `make dev` (or equivalent), and hit a `/healthz` endpoint on each service. No business logic yet.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 0.1 | Finalise repo layout (`apps/course-service`, `apps/user-service`, `apps/cert-service`, `apps/web`, `apps/ai-service`, `infrastructure/`, `docs/`). | DEVOPS | S | — | `tree -L 2` matches docs |
| 0.2 | Author `docker-compose.dev.yml`: MySQL 8 (three databases: `learnpulse_users`, `course_service_db`, `learnpulse_certs`), Kafka (KRaft), Kafka UI, MinIO (local S3), **Redis 7**, **Traefik**. Mailgun API is used directly in dev — no local SMTP container needed; ensure `MAILGUN_API_KEY` and `MAILGUN_DOMAIN` are set in `.env`. | DEVOPS | M | 0.1 | `docker compose up` brings all services healthy |
| 0.3 | Bootstrap LMS Spring Boot 3 app (`apps/course-service`): `pom.xml`, `application.yml`, `HealthController`. Wire Flyway against `course_service_db`. | BE-A | M | 0.1 | `GET /actuator/health` → 200 |
| 0.4 | Bootstrap React (Vite) app: routing skeleton with `/learn` and `/teach` namespaces (PRD §5.7). | FE-A | M | 0.1 | Two empty dashboards render |
| 0.5 | Bootstrap FastAPI service: `requirements.txt`, `app/main.py`, `/healthz`. | AI | S | 0.1 | `curl http://localhost:9000/healthz` → 200 |
| 0.6 | `infrastructure/kafka/topics.sh` — create all five topics + DLQs per `kafka-events.md` §2. | DEVOPS | S | 0.2 | `kafka-topics --list` shows 10 topics |
| 0.7 | GitHub Actions: lint + build for each app on PR. | DEVOPS | M | 0.3, 0.4, 0.5, 0.12, 0.13 | Failing PR is blocked |
| 0.8 | Shared `.env.example` files for each app + root. | DEVOPS | S | 0.1 | New dev can copy, fill, and run |
| 0.9 | Linear/Trello/GitHub Project board mirroring this plan. | Lead | S | — | Each task has an issue + owner |
| 0.10 | `infrastructure/traefik/traefik.dev.yml` + Docker-label routing on each service container per `api-spec.md` §0.1: `/api/auth/*` → User Service `:8081`, `/api/learner/certificates` + `/api/certificates/*` → Cert Service `:8082`, `/api/*` → Course Service `:8080`, `/` → React SPA. Auth-endpoint rate-limit middleware (10 rpm, burst 5). | DEVOPS | M | 0.2 | `curl http://localhost/api/actuator/health` → 200 via Traefik; `curl http://localhost/api/auth/login` with 11 rapid requests returns at least one `429` |
| 0.11 | Wire Redis 7 in `docker-compose.dev.yml` (port 6379, no auth in dev). Verify connectivity with `redis-cli ping`. | DEVOPS | S | 0.2 | `PONG` response in local shell |
| 0.12 | Bootstrap User Service Spring Boot 3 app (`apps/user-service`): `pom.xml`, `application.yml` (datasource `learnpulse_users`), `HealthController`. Wire Flyway. | BE-A | M | 0.1 | `GET /actuator/health` → 200 on `:8081` |
| 0.13 | Bootstrap Certificate Service Spring Boot 3 app (`apps/cert-service`): `pom.xml`, `application.yml` (datasource `learnpulse_certs`, S3 config, Kafka consumer config), `HealthController`. Wire Flyway. | BE-B | M | 0.1 | `GET /actuator/health` → 200 on `:8082` |

**Phase 0 DoD:** Any team member can run the whole stack locally, hit health endpoints on all three backend services via Traefik on port 80, and `redis-cli ping` succeeds.

---

## Phase 1 — Auth & User Domain (Week 2)

> **Service context:** All backend tasks in this phase belong to the **User Service** (`apps/user-service`) and its own database (`learnpulse_users`). The User Service is the sole issuer of JWTs and the authority on user identity. Other services **never** validate JWTs directly. Instead, Traefik's ForwardAuth middleware forwards every protected request to `GET /api/auth/validate` on the User Service, which validates the token, checks the Redis blacklist, and returns `X-User-Id`, `X-User-Email`, and `X-User-Roles` response headers. Traefik injects those headers into the upstream request; downstream services read them to identify the caller.

**Goal:** Users can register, log in, and the JWT/role machinery is in place. The frontend has a working login + protected-route guard.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 1.1 | Flyway migrations V1 (`users`, `user_roles`) per `ERD.md` §2.1–2.2. | BE-A | S | 0.12 | Tables created on startup in `learnpulse_users` |
| 1.2 | JPA entities + repositories: `User`, `UserRole`. | BE-A | S | 1.1 | Unit test: save+load round-trip |
| 1.3 | `POST /api/auth/register` (learner + instructor variants). BCrypt cost 12. | BE-A | M | 1.2 | Postman: register learner, register instructor → 201 |
| 1.4 | `POST /api/auth/login`, `POST /api/auth/refresh`. JWT carries `sub`, `email`, `roles`. | BE-A | M | 1.3 | Login returns access + refresh tokens; bad password → 401 |
| 1.5 | Spring Security filter chain in **User Service only**: JWT auth filter, role-based `@PreAuthorize` annotations, and a `GET /api/auth/validate` endpoint for Traefik's ForwardAuth middleware. Configure ForwardAuth middleware in Traefik to call this endpoint on every non-public route; the endpoint returns `X-User-Id`, `X-User-Email`, and `X-User-Roles` headers on success. Course Service and Certificate Service wire a lightweight header-reading filter that builds the Spring Security context from these headers — no JWT dependency in downstream services. | BE-A | M | 1.4 | Protected endpoint without token → 401 at Traefik; valid token → Traefik injects headers → 200 on all three services; `@PreAuthorize` role checks work in Course and Cert services |
| 1.6 | `GET /api/users/me`, `PATCH /api/users/me`. | BE-B | S | 1.5 | Demo updating own profile |
| 1.7 | Admin endpoints: list users, promote, suspend, reinstate. Suspended users get 403 on next request (`api-spec.md` §3). | BE-B | M | 1.5 | Suspending mid-session blocks subsequent calls |
| 1.8 | Seed first admin via `V2__seed_admin.sql` in the User Service (reads from env vars at startup). | BE-B | S | 1.1 | Fresh DB has one admin |
| 1.9 | Frontend: login page, register page (with "Register as Instructor" toggle). | FE-A | M | 1.4 | Manual flow works against local API |
| 1.10 | Frontend: auth context + protected route HOC. Token stored in `httpOnly` cookie or memory + refresh flow. | FE-A | M | 1.9 | Page reload keeps user logged in until refresh expires |
| 1.11 | Frontend: `<RoleGuard>` component for route protection, role switcher in navbar (visible only to dual-role users — PRD §5.7). | FE-B | M | 1.10 | Toggle navigates between `/learn/*` and `/teach/*` |
| 1.12 | Tests: unit on `JwtService`, integration on `/api/auth/*`. | BE-A | S | 1.4 | CI green |
| 1.13 | Wire `spring-boot-starter-data-redis` (Lettuce) in the User Service. Configure `RedisTemplate` bean. All three backend services share the same Redis instance. | BE-A | S | 0.11 | Integration test writes + reads a key |
| 1.14 | JWT blacklist in Redis: on `PATCH /api/admin/users/{id}/suspend` (User Service), write `blacklist:user:<id>` key (TTL = 7 days). The `/api/auth/validate` endpoint (called by Traefik ForwardAuth on every request) checks this key and returns 403 if present — all downstream services are protected automatically through the gateway. On reinstate, delete the key. | BE-B | M | 1.13, 1.7 | Suspend user mid-session; next API call to any service returns 403 within same JWT lifetime |

**Phase 1 DoD:** End-to-end demo — register a learner, register an instructor, log in, switch modes, hit a protected admin endpoint that 403s for non-admins. Suspend a logged-in user and confirm the immediate 403.

---

## Phase 2 — Course Authoring (Week 3)

> **Service context:** All backend tasks in this phase belong to the **Course Service** (`apps/course-service`) and its database (`course_service_db`).

**Goal:** Instructors can create courses, add modules and lessons, reorder them, and publish. Locking is wired up but not yet triggered (no learners have started anything).

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 2.1 | Flyway V1 (`courses`), V2 (`modules`, `lessons`, `lesson_attachments`) in Course Service. | BE-A | S | 0.3 | Tables exist with constraints from `ERD.md` |
| 2.2 | Entities + repositories for course graph. Eager-load by ID for the read view; lazy elsewhere. | BE-A | M | 2.1 | Repository tests pass |
| 2.3 | `CourseService.create()` — auto-generate enrolment code for `PRIVATE`. | BE-A | M | 2.2 | Private course → `enrolment_code` populated; public → null |
| 2.4 | Course REST endpoints (`api-spec.md` §4): create, list, get, update, list own. | BE-A | M | 2.3 | Postman collection passes |
| 2.5 | Module + Lesson REST endpoints (`api-spec.md` §5). Reorder via `orderIndex`. | BE-B | L | 2.4 | Cannot create lesson outside owned course; ordering preserved |
| 2.6 | `CourseLockGuard` aspect — any write to a `is_locked=1` course → `409 COURSE_LOCKED`. | BE-B | M | 2.5 | Manually flip flag in DB; updates fail with structured error |
| 2.7 | `POST /api/courses/{id}/publish` — validates ≥ 1 module + each module ≥ 1 lesson. Stub event emit for now. | BE-A | M | 2.5 | Empty course → 422; valid course → 200 |
| 2.8 | Admin `DELETE /api/courses/{id}` — cascades. | BE-B | S | 2.4 | Delete course; modules/lessons gone |
| 2.9 | Frontend: instructor "My Courses" page (`/teach/courses`). | FE-A | M | 2.4 | Lists owned courses with status |
| 2.10 | Frontend: course editor (modules + lessons) — react-dnd or similar for reorder. | FE-B | L | 2.5 | Reorder persists on save |
| 2.11 | Frontend: publish button + validation messages (422 surfaced inline). | FE-B | S | 2.7 | Empty course shows "needs at least one lesson" |
| 2.12 | Public course list page (`/learn/browse`) — read-only for now. | FE-A | M | 2.4 | Only `PUBLISHED` courses appear; `enrolment_code` not in payload |
| 2.13 | `@Cacheable` on `GET /api/courses` and `GET /api/courses/{id}` using Spring Cache → Redis (TTL 5 min). `@CacheEvict` on publish, update, delete. Key scheme per `api-spec.md` §0.2. | BE-A | M | 1.13, 2.4 | Second call hits Redis (verify via `MONITOR` or Spring Cache stats); publish evicts cache |

**Phase 2 DoD:** Instructor can build a 3-module / 6-lesson course end-to-end and publish it. Learner can browse it but not yet enrol. Course list responses are cached in Redis.

---

## Phase 3 — Enrolment, Progression & Locking (Week 4)

**Goal:** Learners enrol (public + private), start a course (which locks it), complete lessons sequentially, and unlock modules. No certificates yet.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 3.1 | Flyway V3 (`enrolments`, `lesson_progress`, `module_unlocks`) in Course Service. | BE-A | S | 2.1 | Tables + constraints in place |
| 3.2 | Entities + repositories for enrolment domain. | BE-A | S | 3.1 | Repository tests pass |
| 3.3 | `POST /api/enrolments` — public + private (with code) flows. Unique `(user_id, course_id)`. | BE-A | M | 3.2 | Duplicate enrolment → 409 |
| 3.4 | `POST /api/enrolments/{id}/start` — sets `started_at`, locks the course (atomic), seeds `module_unlocks` for module 1. Idempotent. | BE-A | M | 3.3, 2.6 | Calling twice returns same `startedAt` |
| 3.5 | `POST /api/lessons/{id}/complete` — full validation chain (`api-spec.md` §7): enrolment exists, module unlocked, prereqs done. Out-of-order → 409. | BE-B | L | 3.4 | Skipping a lesson returns `LESSON_OUT_OF_ORDER` |
| 3.6 | Module-unlock side effect: when last lesson in a module completes, insert next `module_unlocks` row OR mark enrolment `COMPLETED` if final module. | BE-B | M | 3.5 | DB trace shows correct state transitions |
| 3.7 | `GET /api/enrolments/{id}/progress` — full tree with `completed`/`unlocked` flags. | BE-B | M | 3.5 | Progress matches DB state |
| 3.8 | `GET /api/learner/enrolments` — list summary. | BE-A | S | 3.3 | Lists with progress percentages |
| 3.9 | Admin enrol/unenrol endpoints. | BE-A | S | 3.3 | Admin can manually enrol any user |
| 3.10 | Frontend: course detail page with "Enrol" / "Request Access" CTA + private-code modal. | FE-A | M | 3.3 | Wrong code shows error in modal |
| 3.11 | Frontend: learner dashboard (`/learn/courses`) with "Start Course" button. Confirmation dialog ("This will lock the course for editing"). | FE-A | M | 3.4 | Shows `startedAt` after click |
| 3.12 | Frontend: course player UI — sequential lesson list, lock icons on future modules, "Mark complete" button. | FE-B | L | 3.5, 3.6, 3.7 | Cannot complete future lessons; UI unlocks next module instantly |
| 3.13 | Frontend: instructor view becomes read-only for locked courses (banner explains why). | FE-B | S | 3.4 | Edit buttons hidden / disabled |

**Phase 3 DoD:** A learner enrols, starts, and completes a multi-module course; instructor sees the course locked; analytics not yet wired.

---

## Phase 4 — Kafka Backbone & Email Pipeline (Week 5)

**Goal:** Real Kafka events replace the stubs. Mailgun API sends welcome and module-unlocked emails. Idempotency is proven by tests.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 4.1 | Kafka producer config in Spring (`acks=all`, `enable.idempotence=true`). | BE-A | S | 0.6 | Producer bean wired with idempotent settings |
| 4.2 | Flyway V4 (`idempotency_log`, `outbox_events`) in Course Service. | BE-A | S | 3.1 | Tables created |
| 4.3 | Outbox table + `OutboxPublisher` scheduled job (`kafka-events.md` §5.1). | BE-A | M | 4.1 | DB row → Kafka topic within 1s |
| 4.4 | Replace `course.published` stub with real event emission via outbox. Schema per `kafka-events.md` §4.1. | BE-A | S | 4.3, 2.7 | Publish a course; consumer logs the event |
| 4.5 | Emit `user.enrolled` from enrolment service via outbox. | BE-A | S | 4.3, 3.3 | Enrolling produces event |
| 4.6 | Emit `module.unlocked` and `course.completed` from progress service. Final-module rule: emit `course.completed`, NOT `module.unlocked`. | BE-B | M | 4.3, 3.6 | Final lesson → only `course.completed` |
| 4.7 | `EmailConsumer` in **User Service** (Kafka group `email-service`): handle `user.enrolled` and `module.unlocked`. Mailgun API (same credentials in dev and prod). Idempotency-log check. | BE-B | L | 4.5, 4.6 | Welcome email delivered via Mailgun in dev |
| 4.8 | Integration test with `EmbeddedKafka`: duplicate `eventId` → single email sent. | BE-B | M | 4.7 | Test passes deterministically |
| 4.9 | DLQ wiring + dashboard panel (Kafka UI is enough for the capstone). | DEVOPS | S | 4.7 | Manually poisoned message lands in `*.dlq` |

**Phase 4 DoD:** Enrolling and completing modules triggers real emails through the Kafka pipeline; idempotency tests are green.

---

## Phase 5 — Certificate Generation (Week 6)

> **Service context:** This phase builds the **Certificate Service** (`apps/cert-service`) — a standalone Spring Boot application with its own database (`learnpulse_certs`). It consumes `course.completed` from Kafka, generates the PDF, and exposes the certificate endpoints. Email sending (welcome, module-unlocked, certificate delivery) lives in the **User Service** alongside the Mailgun client, consumed via Kafka events.

**Goal:** Course completion produces a PDF certificate, stores it in S3 (MinIO locally), inserts the certificate row in the **same DB transaction** as `idempotency_log` (in the Certificate Service's database), and emails the learner.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 5.1 | S3 client wrapper (`software.amazon.awssdk` v2) in Certificate Service. Configurable endpoint for MinIO in dev. | BE-A | S | 0.13 | Smoke test uploads a file to MinIO |
| 5.2 | Thymeleaf certificate template + Flying Saucer renderer in Certificate Service. Template includes: learner name, course, instructor, date, cert UUID, logo. To get learner and course details, the Certificate Service calls the User Service and Course Service REST APIs using the `course.completed` event's `userId` and `courseId`. | BE-B | M | 5.1 | Render & open a sample PDF |
| 5.3 | `CertificateConsumer` in Certificate Service (group `certificate-service`) consuming `course.completed`. Implements the exactly-once flow from `kafka-events.md` §4.4. Flyway V1 (`certificates`, `idempotency_log`) runs on Certificate Service startup. | BE-A | L | 4.6, 5.2 | Single message → single row in `certificates` in `learnpulse_certs` |
| 5.4 | After successful commit in Certificate Service, emit `certificate.generated` via outbox. | BE-A | S | 5.3, 4.3 | Topic receives event |
| 5.5 | Extend `EmailConsumer` (User Service) to handle `certificate.generated` (template `certificate_delivery`). | BE-B | S | 4.7, 5.4 | Mailgun delivers email with download link |
| 5.6 | `GET /api/learner/certificates` and `GET /api/certificates/{id}/download` (signed S3 URL, 5 min TTL) — served by Certificate Service; Traefik routes these paths to it automatically. | BE-A | M | 5.3 | Click link → PDF downloads |
| 5.7 | Concurrency test: two consumer threads receive the same `eventId` simultaneously → exactly one row in `certificates`, one row in `idempotency_log` (Certificate Service DB). | BE-A | M | 5.3 | Test passes ≥ 100 iterations |
| 5.8 | Frontend: "My Certificates" page (`/learn/certificates`). | FE-A | M | 5.6 | Lists and downloads |
| 5.9 | Frontend: completion celebration screen at end of final lesson. Polls `/api/learner/certificates` until cert appears or shows `CERTIFICATE_NOT_READY` gracefully. | FE-B | M | 5.6 | Cert appears within 30s (PRD NFR §10) |

**Phase 5 DoD:** Completing a course produces exactly one PDF, stored in S3, sent by email, downloadable from the dashboard. Concurrency test green.

---

## Phase 6 — AI Study Assistant (Week 7)

**Goal:** A learner inside a started course can chat with a per-course assistant whose answers are grounded in that course's lessons.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 6.1 | FastAPI app structure: `app/main.py`, `app/kafka/consumer.py`, `app/rag/`, `app/api/`. | AI | S | 0.5 | Skeleton boots |
| 6.2 | `aiokafka` consumer for `course.published`. Uses group `ai-service-indexer`. | AI | M | 4.4, 6.1 | Receiving a published course logs payload |
| 6.3 | Embedding + chunking pipeline. Embedding model: `sentence-transformers/all-MiniLM-L6-v2` (local) OR Cerebras-hosted if available. | AI | M | 6.2 | Lessons are chunked + embedded |
| 6.4 | ChromaDB integration: persistent local store at `./chroma_data`. Namespace by `course_id`. | AI | M | 6.3 | Inspecting Chroma shows N vectors per course |
| 6.5 | `POST /ai/courses/{courseId}/chat` endpoint with `userId`, `message`, `chatHistory`. | AI | M | 6.4 | Returns reply + sources |
| 6.6 | LangChain RAG chain wired to `langchain-cerebras` (`ChatCerebras`, model `llama-3.3-70b`). System prompt restricts answers to retrieved chunks (PRD §7.1). | AI | M | 6.5 | Out-of-scope question → polite refusal |
| 6.7 | Service-to-service shared secret (`X-Service-Auth`) verified by FastAPI. | AI | S | 6.5 | Wrong secret → 401 |
| 6.8 | Spring Boot proxy `POST /api/courses/{courseId}/ai/chat` — verifies enrolment + `started_at`, then forwards. | BE-A | M | 6.5, 3.4 | Non-enrolled user → 403 |
| 6.9 | Frontend: chat panel inside the course player. Streaming optional. | FE-A | L | 6.8 | Learner asks "What is REST?" → grounded answer with sources |
| 6.10 | Tests: deterministic smoke test that publishes a fixture course event and asserts a known question retrieves the right lesson. | AI | M | 6.6 | CI green |

**Phase 6 DoD:** Live demo — instructor publishes a 3-lesson course, learner enrols, starts, asks 3 questions, AI cites the correct lessons.

---

## Phase 7 — Analytics & Polish (Week 8)

**Goal:** Instructors and admins get actionable dashboards. UX rough edges sanded down. Project demo-ready.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 7.1 | `GET /api/instructor/courses/{id}/analytics` — aggregate + per-learner. SQL is read-heavy; add indexes per `ERD.md` §2.7. | BE-A | M | 3.7 | Numbers match a hand-counted fixture |
| 7.2 | `GET /api/admin/analytics` — platform-wide. | BE-A | S | 7.1 | Admin dashboard populated |
| 7.3 | Frontend: instructor analytics page — charts for completion rate, per-learner table. | FE-A | L | 7.1 | Sortable table, completion donut |
| 7.4 | Frontend: admin dashboard — users / courses / enrolments tabs with the admin actions (promote, suspend, delete course, manual enrol). | FE-B | L | 1.7, 2.8, 3.9 | All admin flows clickable |
| 7.5 | Empty states + error toasts across the app. Map all `error.code` values to friendly messages. | FE-A + FE-B | M | All FE | Triggering each error code shows readable copy |
| 7.6 | Loading skeletons and 404 page. | FE-B | S | All FE | Slow 3G demo still feels OK |
| 7.7 | Verify Traefik rate-limit middleware from Phase 0.10 is effective in the staging deploy. Tune `burst` and `average` values if needed. | DEVOPS | S | 0.10, 8.4 | `wrk`/k6 hammering `/api/auth/login` → 429 responses from Traefik |
| 7.8 | Logging + `traceId` propagation across Spring Boot ↔ Kafka ↔ FastAPI. | BE-A + AI | M | All BE | A single learner-action trace can be reconstructed |
| 7.9 | README walkthrough + architecture diagram (re-export from PRD §8). Must cover the three-service layout (User, LMS, Cert) and Traefik routing. | DEVOPS | S | — | Fresh dev follows README and demos in 15 min |
| 7.10 | `@Cacheable` on analytics endpoints (`cache:analytics:instructor:<courseId>` and `cache:analytics:admin`, 60 s TTL). Evict on enrolment + completion events. | BE-A | S | 7.1, 1.13 | Analytics endpoint returns Redis-cached data on repeat call; stale after 60 s max |
| 7.11 | AI reply cache in Redis (FastAPI + `redis.asyncio`): key = `sha256(courseId + normalise(message))`, TTL 1 hour. On cache hit, skip Cerebras call entirely. | AI | S | 6.6, 0.11 | Repeated identical question returns instantly; Cerebras RPM counter unchanged |

**Phase 7 DoD:** Capstone-quality demo. Instructor and admin dashboards complete. Logs are searchable. Caching and rate-limiting validated. README onboards a new contributor.

---

## Phase 8 — Hardening & Submission (Week 9 — buffer)

**Goal:** Bug bash, documentation, deployment dry-run.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 8.1 | Whole-team bug bash — 1.5 hours, structured: each tester gets a persona (admin, instructor, learner). | All | S | Phase 7 | Issues filed and triaged |
| 8.2 | Performance check: p95 latency on key endpoints under 50 concurrent users (k6 script). Targets: PRD §10. | BE-B + DEVOPS | M | Phase 7 | Report committed to `docs/perf.md` |
| 8.3 | Security check: OWASP top-10 sweep, JWT secret rotation, S3 bucket policy review, no enrolment codes in public payloads, Redis `requirepass` set in staging, Traefik dashboard disabled in prod, `actuator` endpoints IP-whitelisted via Traefik middleware. | BE-A + DEVOPS | M | Phase 7 | Checklist in `docs/security.md` |
| 8.4 | Production-style deploy dry-run: `infrastructure/traefik/traefik.prod.yml` with TLS (Let's Encrypt or self-signed for assessors). Confirm Traefik → all three Spring Boot services → FastAPI chain works end-to-end. | DEVOPS | L | 8.3 | Live URL accessible to assessors; all three services reachable via correct path prefixes |
| 8.5 | Demo script + recording. | Lead + 1 | M | 8.4 | 10-minute video walks the user journey |
| 8.6 | Capstone submission package: link to repo, deployed URL, demo video, this `plan.md` + PRD. | Lead | S | 8.5 | Submitted by deadline |

**Phase 8 DoD:** Capstone delivered.

---

## Cross-Cutting Tracks

These don't fit a single phase — assign owners and pull throughout the project.

| Track | Owner | Notes |
|---|---|---|
| **Tests** | Whoever writes the code | Aim for: ≥ 80 % service-layer coverage on backend; one happy-path E2E per Phase DoD. |
| **CI/CD** | DEVOPS | Lint → build → test → docker image. Branch protection on `main`. |
| **Observability** | BE-A / DEVOPS | Structured JSON logs, `traceId` per request, Kafka UI for topic depth. Redis `INFO stats` (keyspace hits/misses) tracked in the Phase 8 perf report. Prometheus + Grafana monitoring stack (Phase 10) — all four services expose metrics endpoints; the auto-provisioned **LearnPulse Overview** Grafana dashboard covers JVM health, HTTP request rates, and AI service latency. |
| **Traefik config** | DEVOPS | Two configs: `traefik.dev.yml` (dynamic config, no TLS, dashboard on `:8080`) and `traefik.prod.yml` (TLS via Let's Encrypt / self-signed, dashboard disabled). Both live under `infrastructure/traefik/`. Docker-compose labels on each service container define routing rules and middleware. |
| **Documentation** | All | Update relevant doc when a behaviour changes. PRs that change an API also touch `api-spec.md`. |

---

## Phase 9 — Content Upload & Display

> **Service context:** Backend changes in course-service. Frontend in `apps/web`. Object storage via MinIO (dev) / S3 (prod).

**Goal:** Instructors can upload video, document, and Markdown article files directly through the Course Builder. Learners see a type-appropriate viewer (HTML5 player, PDF iframe, Markdown renderer). Legacy lessons with a `content_url` continue to work via fallback.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 9.1 | Implement `S3Config` bean in course-service (mirrors cert-service; adds `public-endpoint` override for presigned URLs so browsers can reach MinIO directly). | BE-A | S | 0.2 | `S3Client` + `S3Presigner` beans initialise on startup |
| 9.2 | Implement `StorageService` in course-service: `presignUploadUrl`, `presignDownloadUrl`, `delete`. | BE-A | S | 9.1 | Manual test: generate presigned PUT URL, upload a file, presigned GET URL returns it |
| 9.3 | Flyway `V5__add_content_key.sql`: add `content_key` to `lessons`, `s3_key` to `lesson_attachments`, make `s3_url` nullable. Update `Lesson` and `LessonAttachment` JPA entities. | BE-A | S | — | Migrations apply cleanly on fresh DB |
| 9.4 | Create `LessonContentService` and `LessonContentController` (7 endpoints: content upload-url, confirm, GET, DELETE; attachment upload-url, confirm, GET download-url). | BE-A | M | 9.1, 9.2, 9.3 | Postman: full upload → confirm → GET flow for each content type |
| 9.5 | `docker-compose.dev.yml`: add `APP_S3_ACCESS_KEY/SECRET_KEY/PUBLIC_ENDPOINT` to course-service; update `minio-init` to configure bucket CORS for browser direct-upload. | DEVOPS | S | 9.1 | MinIO PUT from browser succeeds without CORS error |
| 9.6 | Frontend: add 7 content API methods to `courseService.js`. | FE-A | S | 9.4 | Methods callable from browser console without errors |
| 9.7 | Frontend: build `LessonContentUpload` component (file picker for VIDEO/DOCUMENT, Markdown textarea for ARTICLE, progress bar, two-step upload flow). | FE-A | M | 9.6 | Instructor uploads a PDF and sees "Uploaded successfully" |
| 9.8 | Frontend: build `LessonContentViewer` component (HTML5 video, PDF iframe + download, react-markdown renderer, legacy fallback). | FE-B | M | 9.6 | Learner sees video playing / PDF rendered / Markdown text |
| 9.9 | Wire `LessonContentUpload` into `CourseBuilder.jsx` (replace Content URL input). Wire `LessonContentViewer` into `CoursePlayer.jsx`. | FE-A/B | S | 9.7, 9.8 | End-to-end: upload video in builder → learner sees player in player view |

**Phase 9 DoD:**
- Instructor uploads a video, a PDF document, and a Markdown article through the Course Builder.
- Enrolled learner sees the correct viewer for each type in the Course Player.
- Legacy lessons with `content_url` (and no `content_key`) still render via the fallback URL.
- An instructor can upload a supplementary attachment; an enrolled learner can download it.

---

## Phase 10 — App Monitoring (Prometheus + Grafana)

> **Service context:** Adds a metrics pipeline across all four services. No new business logic — purely observability infrastructure.

**Goal:** All services expose Prometheus metrics. Prometheus scrapes them every 15 s. Grafana auto-provisions a **LearnPulse Overview** dashboard showing JVM health, HTTP request rates, and AI service latency on every `docker compose up`.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 10.1 | Add `micrometer-registry-prometheus` dependency to `pom.xml` in all three Spring Boot services (user-service, course-service, cert-service). | DEVOPS | S | 0.3, 0.12, 0.13 | Dependency resolves in Maven build |
| 10.2 | Expose `prometheus` actuator endpoint in each Spring Boot `application.yml`; add `management.metrics.tags.application` for per-service labelling in Prometheus. | BE-A | S | 10.1 | `GET /actuator/prometheus` on each service returns Prometheus text format |
| 10.3 | Add `prometheus-fastapi-instrumentator>=6.0.0` to AI service `requirements.txt`; wire `Instrumentator().instrument(app).expose(app)` in `app/main.py`. | AI | S | 0.5 | `GET http://localhost:9000/metrics` returns Prometheus text format |
| 10.4 | Create `infrastructure/monitoring/prometheus.yml` — global 15 s scrape interval; one job per service (`/actuator/prometheus` for Spring Boot services, `/metrics` for AI service) plus Prometheus self-scrape. | DEVOPS | S | 10.2, 10.3 | Prometheus UI at http://localhost:9090/targets shows all five targets `State = UP` |
| 10.5 | Create Grafana provisioning tree under `infrastructure/monitoring/grafana/provisioning/`: datasource pointing at `http://prometheus:9090`; dashboard provider pointing at same directory; `learnpulse.json` dashboard with three rows — **JVM** (heap, GC, threads, CPU), **HTTP** (req/s, p99 latency, 5xx rate), **AI Service** (req/s, p99 latency). | DEVOPS | M | 10.4 | On fresh `docker compose up`, Grafana → Dashboards → **LearnPulse Overview** loads with live data and no manual steps |
| 10.6 | Add `prometheus` (image `prom/prometheus:v2.53.0`, port 9090) and `grafana` (image `grafana/grafana:11.1.0`, port 3000) services to `docker-compose.dev.yml`; add `prometheus-data` and `grafana-data` named volumes. Default Grafana admin password: `admin`. | DEVOPS | S | 10.4, 10.5 | `docker compose up` brings both containers healthy; http://localhost:3000 reachable |

**Phase 10 DoD:** Running `docker compose -f docker-compose.dev.yml up` brings Prometheus to http://localhost:9090 and Grafana to http://localhost:3000. All Spring Boot `/actuator/prometheus` endpoints and the AI service `/metrics` endpoint return scrape data. The provisioned **LearnPulse Overview** dashboard displays live metrics without any manual configuration.

---

## Phase 11 — Quizzes (feature/quiz)

> **Service context:** All backend changes in `apps/course-service`. Frontend changes in `apps/web`. Quizzes are first-class module items alongside lessons: they participate in module-completion gating and can be drag-reordered with lessons.

**Goal:** Instructors can create quizzes, add multiple-choice / true-false questions, and set a passing score. Learners must pass all quizzes in a module (in addition to completing all lessons) before the next module unlocks. Instructors and learners can both reorder quizzes via drag-and-drop in the course builder.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 11.1 | Flyway `V10__create_quiz_tables.sql`: `quizzes`, `quiz_questions`, `quiz_options`, `quiz_attempts`. Foreign keys to `modules` and `enrolments`. | BE-A | S | 3.1 | Tables created on fresh DB startup |
| 11.2 | JPA entities: `Quiz`, `QuizQuestion`, `QuizOption`, `QuizAttempt`. Cascade `PERSIST`/`MERGE`/`REMOVE` on question→option. Repositories: `QuizRepository`, `QuizAttemptRepository`. | BE-A | M | 11.1 | Repository save+load round-trip tests pass |
| 11.3 | Refactor `ModuleProgressChecker`: replace inline lesson-only completion logic with a shared service that gates on both all lessons done **and** all quizzes passed (distinct best attempt ≥ passing score). Used by both `LessonProgressService` and `QuizAttemptService`. | BE-A | M | 11.2, 3.6 | Completing all lessons with an unpassed quiz does NOT unlock next module; passing quiz with all lessons done DOES unlock it |
| 11.4 | Quiz instructor CRUD endpoints (`api-spec.md`): `POST`, `GET`, `PATCH`, `DELETE` under `/api/courses/{courseId}/modules/{moduleId}/quizzes`. `PUT /{quizId}/questions` replaces all questions atomically. All guarded by `@PreAuthorize("hasRole('INSTRUCTOR')")` and `courseService.loadAndGuard()`. | BE-A | M | 11.2 | Postman: create quiz with questions; update title; delete |
| 11.5 | Quiz player endpoint `GET /api/quizzes/{quizId}/player` (learner-facing): verifies active enrolment + module unlock; returns questions with options but **without** `isCorrect` flags. | BE-B | S | 11.2, 3.4 | Non-enrolled learner → 404; locked module → 404; enrolled + unlocked → options returned without correct flag |
| 11.6 | Quiz attempt submission `POST /api/quizzes/{quizId}/attempts`: scores `answers` map against correct options, computes percentage score, evaluates pass/fail vs `passing_score`, saves `QuizAttempt`, runs `ModuleProgressChecker`, returns per-question feedback with `correctOptionId`. | BE-B | M | 11.3, 11.5 | Score 0–100 computed correctly; passing triggers module unlock side-effect |
| 11.7 | Best attempt retrieval `GET /api/quizzes/{quizId}/attempts/best`: returns highest-scoring attempt for the caller, or `null` data if none. | BE-B | S | 11.6 | No attempt → 200 with null data; after attempts → returns highest score |
| 11.8 | Quiz reorder endpoint `PUT /api/courses/{courseId}/modules/{moduleId}/quizzes/reorder`: shift-then-update pattern matching lesson reorder. `quizRepository.shiftOrderIndicesUp()` + `updateOrderIndex()` per item. | BE-A | S | 11.2 | Reorder two quizzes; DB reflects new orderIndex values |
| 11.9 | Frontend `QuizEditor` component: edit quiz metadata (title, passing score) and questions (add / remove question, add / remove option, mark correct, set question type). Wired into `CourseBuilder.jsx` right panel when a quiz is selected. | FE-A | L | 11.4 | Instructor adds a TRUE_FALSE question with two options, saves, sees it reflected |
| 11.10 | Add `reorderQuizzes` to `courseService.js`. Refactor `CourseBuilder.jsx` DnD: replace lesson-only `dragLesson` ref with unified `dragItem` ref indexed into the merged lessons+quizzes `items` array. Drop handler reassigns orderIndex 0…N across all items, splits back into lessons/quizzes, and fires both `reorderLessons` and `reorderQuizzes` in parallel. Quiz items gain a grip-vertical handle. `e.stopPropagation()` on item drag/drop prevents spurious module reorder. | FE-B | M | 11.8, 11.9 | Dragging a quiz above a lesson in the tree persists after page reload; module drag still works |
| 11.11 | Tests: fix `LessonProgressServiceTest` (mock `ModuleProgressChecker` instead of removed `ModuleRepository`/`CourseEventProducer`). New: `ModuleProgressCheckerTest`, `QuizServiceTest`, `QuizAttemptServiceTest`, `QuizControllerTest`, `QuizAttemptControllerTest`. Controller tests use H2 + `@MockitoBean` services. | BE-A + BE-B | M | 11.3–11.8 | `./mvnw test` green; 136 tests total |

**Phase 11 DoD:**
- Instructor creates a module with lessons and quizzes, sets a passing score, and reorders items via drag-and-drop.
- Learner completes all lessons but fails the quiz → next module stays locked.
- Learner passes the quiz → next module unlocks (or course completes if final module).
- Per-question feedback (correct/incorrect, correct option revealed) returned on submission.
- All 136 backend tests green.

---

## Phase 12 — AI Course Builder (feature/ai-course-builder)

> **Service context:** New Kafka topics wire the Course Service to the AI Service. The AI Service adds a generation pipeline separate from the existing RAG chat pipeline. The Course Service gets a new `CourseGenerationJob` entity and two instructor endpoints. The frontend gets a modal-driven prompt-to-course flow.

**Goal:** Instructors can enter a text prompt describing a course and have a complete DRAFT course — modules, Markdown lesson content, and per-lesson quizzes — generated automatically by an LLM. Generation runs asynchronously over Kafka; the frontend polls for completion and auto-navigates to the CourseBuilder.

| # | Task | Owner | Est. | Depends on | Acceptance |
|---|---|---|---|---|---|
| 12.1 | `infrastructure/kafka/topics.sh`: add three topics + DLQs — `course.generation.requested`, `course.generation.completed`, `course.generation.failed` (3 partitions each). | DEVOPS | S | 0.6 | `kafka-topics --list` shows 6 new topics |
| 12.2 | Flyway `V11__add_generation_job.sql`: `course_generation_jobs` table (UUID PK, `instructor_id`, `prompt TEXT`, `status ENUM('PENDING','COMPLETED','FAILED')`, `error_message TEXT`, `course_id`, timestamps); `ALTER TABLE lessons ADD COLUMN generated_content MEDIUMTEXT NULL`. | BE-A | S | 2.1 | Tables/column exist on fresh DB |
| 12.3 | `CourseGenerationJob` JPA entity (UUID PK via `@GeneratedValue(strategy=UUID)`); `JobStatus` enum (PENDING / COMPLETED / FAILED); `CourseGenerationJobRepository`. | BE-A | S | 12.2 | Repository save+load round-trip passes |
| 12.4 | Event record DTOs: `CourseGenerationRequestedEvent`, `CourseGenerationCompletedEvent` (nested `GeneratedModule` / `GeneratedLesson` / `GeneratedQuiz` / `GeneratedQuestion` / `GeneratedOption`), `CourseGenerationFailedEvent`. `CourseGenerationProducer` emits `course.generation.requested`. | BE-A | M | 12.3 | Publishing a job logs the emitted event |
| 12.5 | `KafkaConsumerConfig`: dedicated `aiResultsListenerContainerFactory` (manual-ack, `AckMode.MANUAL_IMMEDIATE`) for the two result topics; separate from the existing outbox consumer factory. | BE-A | S | 0.6 | Bean wires without startup errors |
| 12.6 | `CourseGenerationConsumer` in course-service: `@KafkaListener` on `course.generation.completed` + `course.generation.failed`; manual ack; dispatches to `CourseGenerationService`. | BE-A | S | 12.4, 12.5 | Consuming a fixture event calls the correct service method |
| 12.7 | `CourseGenerationService`: `initiate(prompt, instructorId)` — creates PENDING job, fires `requested` event, returns `GenerationJobResponse`. `handleCompleted(event)` — builds full Course / Module / Lesson / Quiz graph, uploads Markdown content per lesson to S3 as `lessons/{id}/content.md`, sets `content_key`, marks job COMPLETED with `courseId`. `handleFailed(event)` — marks job FAILED with `errorMessage`. | BE-A | L | 12.3, 12.4 | End-to-end: fixture completed event → course row + S3 objects + job status = COMPLETED |
| 12.8 | Two new `InstructorController` endpoints: `POST /api/instructor/courses/generate` (body: `{prompt}`) → 202 with `GenerationJobResponse`; `GET /api/instructor/courses/generate/{jobId}` → current `JobStatus` + `courseId` when done. | BE-A | S | 12.7 | Postman: POST returns jobId; GET returns PENDING then COMPLETED after pipeline runs |
| 12.9 | AI service Pydantic schemas (`app/schemas/generation.py`): `CourseGenerationRequestedEvent`, `CourseOutline`, `ModuleOutline`, `LessonOutline`, `GeneratedQuiz`, `QuizQuestion`, `QuizOption`. | AI | S | — | Schemas importable; `model_validate` round-trip test passes |
| 12.10 | `CourseGenerationPipeline` (`app/generation/pipeline.py`): Step 1 — synchronous LLM call (`ChatGroq`, `llama-3.3-70b`) to produce `CourseOutline` (3–5 modules × 3–5 lessons). Step 2 — parallel `llm.abatch()` for Markdown lesson content and quiz JSON for every lesson simultaneously. Assembles completed-event payload. | AI | L | 12.9 | Pipeline called with a fixture prompt returns a valid completed payload with content + quizzes |
| 12.11 | `CourseGenerationConsumer` (aiokafka, `app/kafka/generation_consumer.py`): subscribes to `course.generation.requested`, runs `CourseGenerationPipeline`, emits completed or failed via `GenerationEventProducer`. Manual offset commit after successful publish. | AI | M | 12.10 | Sending a Kafka message triggers pipeline; completed event appears in topic |
| 12.12 | `GenerationEventProducer` (`app/kafka/producer.py`): `publish_completed(payload)` → `course.generation.completed`; `publish_failed(jobId, instructorId, reason)` → `course.generation.failed`. | AI | S | 12.11 | Topics receive correct JSON payloads |
| 12.13 | Frontend: `AiGenerateModal` component — prompt textarea (10–2000 char validation), animated status messages cycling every 20 s ("Designing course outline…", "Writing lesson content…", "Generating quizzes…"), polls `getGenerationJob` every 3 s up to 180 s timeout; on COMPLETED calls `onSuccess(courseId)`; on FAILED shows `errorMessage`. | FE-A | M | 12.8 | Submitting a prompt shows progress UI; COMPLETED auto-navigates to CourseBuilder; FAILED shows error inline |
| 12.14 | `MyCourses.jsx`: "Generate with AI" button (sparkles icon) in page header opens `AiGenerateModal`; `onSuccess` shows toast + navigates to `/teach/courses/{courseId}/edit`. | FE-A | S | 12.13 | Button visible in My Courses header; success navigates to editor |
| 12.15 | `courseService.js`: `generateCourse(body)` → `POST /api/instructor/courses/generate`; `getGenerationJob(jobId)` → `GET /api/instructor/courses/generate/{jobId}`. | FE-A | S | 12.8 | Both methods callable from browser console without errors |
| 12.16 | `CourseBuilder.jsx`: lesson content panel reads `lesson.generatedContent` as the initial Markdown value; `LessonContentUpload` receives `hasContent` flag when either `contentKey` or `generatedContent` is set. | FE-A | S | 12.13 | Opening a generated course shows lesson content pre-filled in the editor |

**Phase 12 DoD:**
- Instructor clicks "Generate with AI", enters a description, and within ~3 minutes a complete DRAFT course (3–5 modules, 3–5 lessons each, per-lesson Markdown content + quiz) appears in the CourseBuilder.
- Frontend shows animated progress messages and polls every 3 s; navigates automatically on completion.
- Generated Markdown is uploaded to S3 and surfaced as an ARTICLE lesson with the existing `LessonContentViewer`.
- FAILED jobs surface the error message inline in the modal.
- All existing course-service and AI-service tests remain green.

---

## Risk Register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Kafka exactly-once subtleties | High | Land Phase 4 early; write the concurrency test (5.7) the day the consumer is born. |
| Cerebras free-tier rate limits during demo | Medium | Redis AI reply cache (task 7.11) absorbs repeated questions. Fallback to a stock "I'm at capacity" message on 429 from Cerebras. |
| Course locking corner cases (race between `start` and `update`) | Medium | Use a DB-level row lock in `start()`; cover with an integration test. |
| S3 / MinIO config drift between dev and staging | Medium | One `S3Client` wrapper; only the endpoint differs via env var. |
| Redis unavailability | Low | Spring Cache `@Cacheable` is non-critical — a `RedisCacheManager` error fallback (`allowInFlightCachePopulationOnMiss=true`) lets requests pass through to the DB. JWT blacklist failure is a security risk; document a circuit-breaker fallback that re-checks `users.status` in DB if Redis is unreachable. |
| Traefik misconfiguration silently breaking SPA routing or service routing | Low | Add an E2E smoke test that hits a deep-link URL (e.g. `/learn/courses/1`) through Traefik and confirms 200 + React root HTML; also hit `/api/auth/login` and `/api/learner/certificates` to confirm correct service routing. |
| Schema breaking changes after launch | Low (capstone scope) | Topic versioning convention in `kafka-events.md` §7. |

---

## Definition of Done — Project Level

A feature is *done* when:
1. Code merged to `main` via reviewed PR.
2. Migrations applied automatically on startup.
3. Tests cover the happy path **and** the documented error path.
4. The relevant doc (`api-spec.md` / `kafka-events.md` / `ERD.md`) is updated in the same PR if behaviour changed.
5. The user-visible flow is demoable in the local stack with no manual DB poking.

---

*End of Document*
