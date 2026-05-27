# LearnPulse

> Intelligent learning, made personal.

An event-driven online learning platform built for both learners and instructors. Structured course progression, AI-assisted study, and automated PDF certificate delivery — all backed by a production-grade microservice architecture.

**Moniepoint DreamDevs Capstone · May 2026**  
**Authors:** Anthony Alikah & John Agene
 An event-driven online learning platform — structured courses, sequential progression, AI-assisted study, and automated PDF certificates.

**Moniepoint DreamDev Capstone · May 2026**

![Java](https://img.shields.io/badge/Java-21+-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-brightgreen?logo=springboot)
![Python](https://img.shields.io/badge/Python-3.12-blue?logo=python)
![React](https://img.shields.io/badge/React-Vite-61DAFB?logo=react)
![Kafka](https://img.shields.io/badge/Kafka-KRaft-black?logo=apachekafka)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)

---

## 📖 Overview

LearnPulse lets instructors publish structured courses (Course → Module → Lesson) and learners progress through them sequentially. On completion, the system generates a PDF certificate and emails it — exactly once, guaranteed via Kafka idempotency. An AI Study Assistant (RAG-backed) answers learner questions about course content in real time.

Instructors can also describe a course topic in plain text and have the AI Course Builder generate a complete, ready-to-publish course — modules, lesson content (Markdown articles), and per-lesson quizzes — in one Kafka-driven async pipeline. Learners build daily learning streaks as they complete lessons, tracked server-side and visible from their dashboard.

**Three roles:** Learner · Instructor · Admin — a single account can hold multiple roles simultaneously.

---

## 🏗️ Architecture

```
Browser
  └─► Traefik :80  (API gateway + JWT ForwardAuth + rate limiting)
        ├─► User Service     :8081  — auth, users, admin
        ├─► Course Service   :8080  — courses, enrolments, progress, analytics
        ├─► Cert Service     :8082  — certificate generation & delivery
        ├─► AI Service       :9000  — RAG chat (FastAPI + LangChain + Groq)
        └─► React SPA               — learner & instructor modes
```

**Async backbone — Apache Kafka (KRaft)**

| Topic | Consumer | Purpose |
|---|---|---|
| `course.published` | AI Service | Index lesson content into ChromaDB |
| `user.enrolled` | Notification | Enrolment confirmation email |
| `module.unlocked` | Notification | Module unlock email |
| `course.completed` | Cert Service | Generate PDF → upload to MinIO |
| `cert.generated` | Notification | Certificate delivery email |
| `course.generation.requested` | AI Service | Generate full course structure + content + quizzes |
| `course.generation.completed` | Course Service | Persist generated course |
| `course.generation.failed` | Course Service | Mark job as failed |

**Storage:** MySQL (one schema per service) · MinIO/S3 · ChromaDB · Redis

> JWT validation is centralised at Traefik via ForwardAuth. Downstream services receive pre-validated headers (`X-User-Id`, `X-User-Roles`) and never touch raw tokens.

Full architecture diagrams, Kafka event payloads, and API reference live in the [`docs/`](docs/) folder.
        └─► React SPA               — learner & instructor modes (/learn, /teach)

Async backbone: Apache Kafka (KRaft)
  course.published              → AI Service (indexes lessons into ChromaDB)
  user.enrolled                 → Email notification
  module.unlocked               → Email notification
  course.completed              → Cert Service (generates PDF → uploads to MinIO)
  cert.generated                → Email delivery
  course.generation.requested   → AI Service (generates course structure + content + quizzes)
  course.generation.completed   → Course Service (persists generated course)
  course.generation.failed      → Course Service (marks job failed)

Storage: MySQL (one schema per service) · MinIO/S3 · ChromaDB · Redis
```

> JWT validation is handled centrally by Traefik via ForwardAuth — downstream services receive pre-validated headers (`X-User-Id`, `X-User-Roles`) and never see raw tokens.

For the full architecture diagram, Kafka event payloads, and API reference see the [docs](docs/PRD.md) folder.

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18 + Vite + React Router |
| API Gateway | Traefik v3 |
| Course / LMS Service | Java 21+ + Spring Boot 3 |
| User Service | Java 21+ + Spring Boot 3 |
| Certificate Service | Java 21+ + Spring Boot 3 |
| AI Service | Python 3.12 + FastAPI + LangChain |
| Databases | MySQL 8 (one schema per service) |
| Cache | Redis 7 |
| Message Broker | Apache Kafka (KRaft, no Zookeeper) |
| Object Storage | MinIO (dev) / AWS S3 (prod) |
| Vector Store | ChromaDB |
| LLM | Groq — Llama 3.3 70B Versatile |
| Email | Mailgun |
| PDF Generation | Thymeleaf + Flying Saucer |
| Monitoring | Prometheus + Grafana |

---

## ⚡ Quick Start

### Prerequisites

| Tool | Version |
|---|---|
| Docker + Docker Compose | 24+ / v2 |
| Git | any |

> Java and Python are only needed if you want to run services outside Docker.

### 1. Clone

```bash
git clone https://github.com/dreyyfuss/learn-pulse.git
cd learn-pulse
```

### 2. Configure the AI service key

The AI service needs one free API key:

- **Groq** — powers the AI Study Assistant (RAG chat), course generation, and voice-to-text transcription via Whisper. Sign up at [console.groq.com](https://console.groq.com) — no credit card required.

```bash
cp apps/ai-service/.env.example apps/ai-service/.env
# Set GROQ_API_KEY=<your-key> in apps/ai-service/.env
```

> The rest of the platform starts fine without this key — AI chat, course generation, and voice transcription will return errors if the key is missing.

### 3. Start the full stack

```bash
docker compose -f docker-compose.dev.yml up --build
```

The first build compiles three Spring Boot services — allow **5–8 minutes**. Subsequent starts use cached images and are fast.

Wait for all three services to report ready:

```
user-service    | Started UserServiceApplication
course-service  | Started CourseServiceApplication
cert-service    | Started CertServiceApplication
```

### 4. Open the app

Navigate to [http://localhost](http://localhost). A seeded admin account is created automatically by Flyway on first boot:

| Field | Value |
|---|---|
| Email | `admin@learnpulse.dev` |
| Password | `Admin@1234!` |

---

## Service URLs

| URL | What's There |
## 🌐 Service URLs

| URL | Service |
|---|---|
| http://localhost | LearnPulse web app |
| http://localhost:8090/dashboard/ | Traefik dashboard |
| http://localhost:8085 | Kafka UI |
| http://localhost:9001 | MinIO console (`minioadmin` / `minioadmin`) |
| http://localhost:3000 | Grafana (`admin` / `admin`) |
| http://localhost:9090 | Prometheus |
| http://localhost:9000/healthz | AI service health |

Direct service ports (bypass Traefik — dev only): User Service `:8081`, Course Service `:8080`, Certificate Service `:8082`, AI Service `:9000`.
| http://localhost:9000/healthz | AI service health check |

**Direct service ports** (bypass Traefik — dev only):

| Service | Port |
|---|---|
| User Service | 8081 |
| Course Service | 8080 |
| Certificate Service | 8082 |
| AI Service | 9000 |

---

## 🔑 Environment Variables

All services default to safe dev values. The only variable you must set is `GROQ_API_KEY` (see [Quick Start](#-quick-start)).

All services default to safe dev values. The only variable you must set yourself is `GROQ_API_KEY`.

| Variable | Required | Default | Description |
|---|---|---|---|
| `GROQ_API_KEY` | Yes | *(empty)* | AI chat, course generation, and transcription |
| `GROQ_LLM_MODEL` | No | `llama-3.3-70b-versatile` | Groq model for chat and course generation |
| `MAILGUN_API_KEY` | No | *(empty)* | Emails silently skipped if blank |
| `MAILGUN_DOMAIN` | No | `sandbox.mailgun.org` | Mailgun sending domain |
| `JWT_SECRET` | No | dev default | Change this in production |
| `SERVICE_AUTH_SECRET` | No | `change-me-in-dev` | Shared secret between Course Service and AI Service |

Override any variable in your shell or in a `.env` file at the project root.

---

## Try the Golden Path

**Core learning flow**

1. Register as an **instructor** → create a course (module + lesson) → publish it.
2. Register as a **learner** → find the course → enrol → start it → mark the lesson complete.
3. Open **Kafka UI** — events should appear on `user.enrolled`, `module.unlocked`, and `course.completed`.
4. Open **MinIO** (`learnpulse` bucket → `certificates/`) — the PDF certificate should be there.

**AI Course Builder**

1. As an instructor, navigate to the Course Builder and describe your topic in plain text.
2. The system generates a full course (modules + lesson articles + quizzes) asynchronously via Kafka.
3. Poll `GET /api/instructor/courses/generate/{jobId}` until `status: COMPLETED`.
4. The generated course appears in your dashboard as a `DRAFT`, ready to review and publish.

**Learning streaks**

1. As a learner, mark any lesson complete.
2. Call `GET /api/learner/streak` — `currentStreak` increments for each day you complete at least one lesson.

---

## Project Structure

```
apps/
  user-service/     — Auth, users, admin
  course-service/   — Courses, enrolments, progress, analytics
  cert-service/     — Certificate generation and delivery
  ai-service/       — RAG chat assistant and course generator
  web/              — React frontend

infrastructure/
  traefik/          — Gateway config and rate-limit tests
  kafka/            — Topic init script
  mysql/            — DB init SQL
  monitoring/       — Prometheus and Grafana provisioning

docs/               — Full documentation (see below)
| Variable | Required | Default | Description |
|---|---|---|---|
| `GROQ_API_KEY` | **Yes** | *(empty)* | AI chat, course generation, and transcription — free tier at console.groq.com |
| `GROQ_LLM_MODEL` | No | `llama-3.3-70b-versatile` | Groq model used for chat and course generation |
| `MAILGUN_API_KEY` | No | *(empty)* | Emails are silently skipped if blank |
| `MAILGUN_DOMAIN` | No | `sandbox.mailgun.org` | Mailgun sending domain |
| `JWT_SECRET` | No | dev default | **Change this in production** |
| `SERVICE_AUTH_SECRET` | No | `change-me-in-dev` | Shared secret between Course Service and AI Service |

Override any variable by setting it in your shell before running `docker compose`, or by adding it to a `.env` file at the project root.

---

## 🚀 Try the Golden Path

1. Register as an **instructor** → create a course (add a module and a lesson) → publish it.
2. Register as a **learner** → find the course → enrol → start it → mark the lesson complete.
3. Check **Kafka UI** — messages should appear on `user.enrolled`, `module.unlocked`, and `course.completed`.
4. Check **MinIO** (`learnpulse` bucket → `certificates/`) — the PDF certificate should be there.

**Try the AI Course Builder:**
1. As an instructor → navigate to the Course Builder → describe your course topic in plain text.
2. The system generates a complete course (modules + lesson articles + quizzes) asynchronously via Kafka.
3. Poll `GET /api/instructor/courses/generate/{jobId}` until `status: COMPLETED`.
4. The generated course appears in your instructor dashboard as a `DRAFT`, ready to review and publish.

**Try learning streaks:**
1. As a learner, mark any lesson complete.
2. Call `GET /api/learner/streak` — `currentStreak` increments each day you complete at least one lesson.

---

## 📂 Project Structure

```
apps/
  user-service/     # Auth, users, admin
  course-service/   # Courses, enrolments, progress, analytics
  cert-service/     # Certificate generation & delivery
  ai-service/       # RAG chat assistant
  web/              # React frontend
infrastructure/
  traefik/          # Traefik config + rate-limit k6 test
  kafka/            # Topic init script
  mysql/            # DB init SQL
  monitoring/       # Prometheus + Grafana provisioning
docs/               # Detailed documentation (see below)
docker-compose.dev.yml
```

---

## 📚 Documentation

| Document | Contents |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | Full product requirements, roles, and business rules |
| [`docs/api-spec.md`](docs/api-spec.md) | Complete REST API reference |
| [`docs/kafka-events.md`](docs/kafka-events.md) | Kafka topics, event payloads, consumer contracts |
| [`docs/schema.md`](docs/schema.md) | Database schema — tables, columns, constraints, migration history |
| [`docs/plan.md`](docs/plan.md) | Implementation plan and architectural decisions |

---

## API Documentation (Swagger)

Each backend service exposes interactive API docs via [springdoc-openapi](https://springdoc.org/). Start the stack first, then open any of these:

| Service | Swagger UI | Raw OpenAPI |
|---|---|---|
| User Service | http://localhost:8081/swagger-ui/index.html | http://localhost:8081/v3/api-docs |
| Course Service | http://localhost:8080/swagger-ui/index.html | http://localhost:8080/v3/api-docs |
| Cert Service | http://localhost:8082/swagger-ui/index.html | http://localhost:8082/v3/api-docs |

To call secured endpoints from the UI: click **Authorize** and enter `Bearer <token>`. Get a token from `POST /api/auth/login`.

---

## API Reference

### User Service — `:8081`

**Authentication**

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | None | Register a new account |
| POST | `/api/auth/login` | None | Login; returns access + refresh tokens |
| POST | `/api/auth/refresh` | None | Exchange refresh token for new access token |
| GET | `/api/auth/validate` | Bearer | Validate JWT (called internally by Traefik) |

**Profile**

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/users/me` | Authenticated | Get the authenticated user's profile |
| PATCH | `/api/users/me` | Authenticated | Update display name or profile fields |

**Admin — Users**

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/admin/users` | ADMIN | List users; filter by `role`, `status`, `q` |
| PATCH | `/api/admin/users/{id}/promote` | ADMIN | Grant the ADMIN role |
| PATCH | `/api/admin/users/{id}/suspend` | ADMIN | Suspend a user account |
| PATCH | `/api/admin/users/{id}/reinstate` | ADMIN | Reactivate a suspended account |

---

### Course Service — `:8080`

**Courses**

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/courses` | Authenticated | List published courses (`q`, `category`, `visibility`) |
| GET | `/api/courses/{id}` | Authenticated | Get full course details |
| POST | `/api/courses` | INSTRUCTOR | Create a course in DRAFT status |
| PATCH | `/api/courses/{id}` | INSTRUCTOR (owner) | Update title, description, or visibility |
| POST | `/api/courses/{id}/publish` | INSTRUCTOR (owner) | Publish the course |
| GET | `/api/courses/{id}/enrolment-code` | INSTRUCTOR (owner) | Get the private enrolment code |
| DELETE | `/api/courses/{id}` | ADMIN | Hard-delete a course |

**Enrolments**

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/enrolments` | LEARNER | Enrol by course ID or enrolment code |
| POST | `/api/enrolments/{id}/start` | LEARNER | Start enrolment, unlocking the first module |
| GET | `/api/enrolments/{id}/progress` | LEARNER / INSTRUCTOR / ADMIN | Module and lesson completion progress |

**Instructor Dashboard**

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/instructor/courses` | INSTRUCTOR | List own courses |
| GET | `/api/instructor/courses/{id}/analytics` | INSTRUCTOR (owner) | Per-course enrolment and completion stats |
| POST | `/api/instructor/courses/generate` | INSTRUCTOR | Submit a prompt to start AI course generation |
| GET | `/api/instructor/courses/generate/{jobId}` | INSTRUCTOR (owner) | Poll AI generation job status |

**Learner Dashboard**

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/learner/enrolments` | LEARNER | List my enrolments |
| GET | `/api/learner/streak` | LEARNER | Get my daily learning streak |

**Modules**

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/courses/{courseId}/modules` | INSTRUCTOR (owner) | Add a module |
| PUT | `/api/courses/{courseId}/modules/reorder` | INSTRUCTOR (owner) | Set module display order |
| PATCH | `/api/courses/{courseId}/modules/{id}` | INSTRUCTOR (owner) | Update title or description |
| DELETE | `/api/courses/{courseId}/modules/{id}` | INSTRUCTOR (owner) | Delete module and its lessons |

**Lessons**

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/courses/{courseId}/modules/{moduleId}/lessons` | INSTRUCTOR (owner) | Add a lesson |
| PUT | `/api/courses/{courseId}/modules/{moduleId}/lessons/reorder` | INSTRUCTOR (owner) | Set lesson display order |
| PATCH | `/api/courses/{courseId}/modules/{moduleId}/lessons/{id}` | INSTRUCTOR (owner) | Update title or description |
| DELETE | `/api/courses/{courseId}/modules/{moduleId}/lessons/{id}` | INSTRUCTOR (owner) | Delete a lesson |

**Lesson Content & Attachments**

> Prefix: `/api/courses/{courseId}/modules/{moduleId}`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `.../lessons/{lessonId}/content/upload-url` | INSTRUCTOR (owner) | Get presigned S3 URL for direct upload |
| POST | `.../lessons/{lessonId}/content/confirm` | INSTRUCTOR (owner) | Confirm S3 upload completed |
| GET | `.../lessons/{lessonId}/content` | Authenticated | Get lesson content |
| DELETE | `.../lessons/{lessonId}/content` | INSTRUCTOR (owner) | Delete lesson content from S3 |
| POST | `.../lessons/{lessonId}/attachments/upload-url` | INSTRUCTOR (owner) | Presigned S3 URL for attachment upload |
| POST | `.../lessons/{lessonId}/attachments/confirm` | INSTRUCTOR (owner) | Confirm attachment upload |
| GET | `.../lessons/{lessonId}/attachments/{attachmentId}/download-url` | Authenticated | Time-limited presigned download URL |

**Lesson Progress**

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/lessons/{id}/complete` | LEARNER | Mark lesson complete; triggers module-completion check |

**Quizzes**

> Instructor route prefix: `/api/courses/{courseId}/modules/{moduleId}`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `.../quizzes` | INSTRUCTOR (owner) | Create a quiz |
| GET | `.../quizzes/{quizId}` | INSTRUCTOR (owner) | Get quiz with all questions and options |
| PATCH | `.../quizzes/{quizId}` | INSTRUCTOR (owner) | Update title, description, or passing score |
| DELETE | `.../quizzes/{quizId}` | INSTRUCTOR (owner) | Delete quiz |
| PUT | `.../quizzes/reorder` | INSTRUCTOR (owner) | Set quiz display order |
| PUT | `.../quizzes/{quizId}/questions` | INSTRUCTOR (owner) | Replace all questions and answer options |
| GET | `/api/quizzes/{quizId}/player` | LEARNER | Get quiz in player format (no answer flags) |
| POST | `/api/quizzes/{quizId}/attempts` | LEARNER | Submit answers; returns score and pass/fail |
| GET | `/api/quizzes/{quizId}/attempts/best` | LEARNER | Get best recorded attempt |

**Admin**

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/admin/analytics` | ADMIN | Platform-wide enrolment and completion stats |
| GET | `/api/admin/courses` | ADMIN | List all courses regardless of status |
| GET | `/api/admin/enrolments` | ADMIN | List all enrolments across the platform |
| POST | `/api/admin/enrolments` | ADMIN | Manually enrol a user |
| DELETE | `/api/admin/enrolments/{id}` | ADMIN | Remove an enrolment |

---

### Cert Service — `:8082`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/learner/certificates` | LEARNER | List all certificates issued to the learner |
| GET | `/api/certificates/{uuid}/download` | LEARNER (owner) | HTTP 302 redirect to 5-minute presigned PDF URL |

---

## Test the API

Replace `<TOKEN>` with the access token from `POST /api/auth/login`. All requests go through the Traefik gateway at `http://localhost`.

```bash
# Register
curl -s -X POST http://localhost/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"learner@example.com","password":"Pass@1234!","fullName":"Jane Doe"}'

# Login — copy accessToken from the response
curl -s -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"learner@example.com","password":"Pass@1234!"}'

# Get my profile
curl -s http://localhost/api/users/me \
  -H "Authorization: Bearer <TOKEN>"

# List published courses
curl -s "http://localhost/api/courses?q=python&size=10" \
  -H "Authorization: Bearer <TOKEN>"

# Create a course (INSTRUCTOR)
curl -s -X POST http://localhost/api/courses \
  -H "Authorization: Bearer <INSTRUCTOR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Intro to Go","description":"Beginner course","category":"Engineering","visibility":"PUBLIC"}'

# Publish the course
curl -s -X POST http://localhost/api/courses/<COURSE_UUID>/publish \
  -H "Authorization: Bearer <INSTRUCTOR_TOKEN>"

# Enrol in a course (LEARNER)
curl -s -X POST http://localhost/api/enrolments \
  -H "Authorization: Bearer <LEARNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"courseId":"<COURSE_UUID>"}'

# Start the enrolment
curl -s -X POST http://localhost/api/enrolments/<ENROLMENT_UUID>/start \
  -H "Authorization: Bearer <LEARNER_TOKEN>"

# Mark a lesson complete
curl -s -X POST http://localhost/api/lessons/<LESSON_UUID>/complete \
  -H "Authorization: Bearer <LEARNER_TOKEN>"

# Get enrolment progress
curl -s http://localhost/api/enrolments/<ENROLMENT_UUID>/progress \
  -H "Authorization: Bearer <TOKEN>"

# Generate a course with AI (INSTRUCTOR)
curl -s -X POST http://localhost/api/instructor/courses/generate \
  -H "Authorization: Bearer <INSTRUCTOR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"A 5-module introduction to distributed systems"}'

# Poll AI generation job status
curl -s http://localhost/api/instructor/courses/generate/<JOB_UUID> \
  -H "Authorization: Bearer <INSTRUCTOR_TOKEN>"

# Submit a quiz attempt (LEARNER)
curl -s -X POST http://localhost/api/quizzes/<QUIZ_UUID>/attempts \
  -H "Authorization: Bearer <LEARNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"answers":[{"questionId":"<Q_UUID>","selectedOptionId":"<OPT_UUID>"}]}'

# Platform analytics (ADMIN)
curl -s http://localhost/api/admin/analytics \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# List my certificates
curl -s http://localhost/api/learner/certificates \
  -H "Authorization: Bearer <LEARNER_TOKEN>"

# Download a certificate PDF
curl -sL http://localhost/api/certificates/<CERT_UUID>/download \
  -H "Authorization: Bearer <LEARNER_TOKEN>" -o certificate.pdf
```

---

## Design Decisions

**Centralised JWT validation via Traefik ForwardAuth.**
JWT parsing happens exactly once — in the User Service's `/api/auth/validate` endpoint. Traefik calls it on every protected request and injects `X-User-Id`, `X-User-Email`, and `X-User-Roles` headers downstream. No service duplicates security logic or carries a JWT library.

**Transactional outbox + idempotency logs.**
Every Kafka event is written to an `outbox_events` table in the same DB transaction as the business mutation. A scheduler relays pending rows to Kafka. Each consumer checks an `idempotency_log` before processing — duplicate deliveries are silently dropped. This gives exactly-once end-to-end semantics even across restarts and provider failures.

**Course lock guard via AOP.**
An `@Around` aspect (`CourseLockGuard`) intercepts all write operations on courses, modules, lessons, and quizzes. Once the first learner starts a course, `course.isLocked()` is set to true and any structural mutation is rejected with `CourseAlreadyStartedException`. Instructors cannot alter live course structure in ways that would break learner progress.

**Sequential module progression.**
Learners cannot access a module until the previous one is fully complete. `ModuleProgressChecker` evaluates completion after every `POST /api/lessons/{id}/complete` and writes a `module_unlock` record when the threshold is met.

**Two-phase S3 upload.**
Instructors receive a presigned S3 PUT URL and upload directly from the browser — the application never proxies large files through memory. They then call `/confirm` to register the S3 key. Application memory stays flat regardless of file size.

**Redis caching with restricted deserialisation.**
Course list and detail results are cached with a 5-minute TTL. Analytics are cached at 60 seconds. Jackson default typing is restricted to `com.courseservice.*` and `java.*` to prevent deserialisation gadget attacks.

---

## Performance Notes

**Redis** — 5-minute TTL for course data; 60-second TTL for analytics aggregations.

**Covering index on enrolments** — `(course_id, status, user_id, enrolled_at, completed_at)` satisfies both aggregate counts and per-learner analytics queries without a table scan (Flyway V7).

**Lesson progress index** — `idx_lesson_progress_lesson_id` on `lesson_progress` (Flyway V5).

**Idempotent Kafka producer** — `enable.idempotence=true` and `acks=all` in Course Service prevents duplicate messages from network retries.

**S3 presigned URLs** — video and document content streams directly from object storage; no large-file proxying through the application layer.
| [`docs/PRD.md`](docs/PRD.md) | Full product requirements — features, roles, business rules |
| [`docs/api-spec.md`](docs/api-spec.md) | Complete REST API reference |
| [`docs/kafka-events.md`](docs/kafka-events.md) | Kafka topics, event payloads, consumer contracts |
| [`docs/schema.md`](docs/schema.md) | Database schema — all tables, columns, constraints, and migration history |
| [`docs/plan.md`](docs/plan.md) | Implementation plan and architectural decisions |
