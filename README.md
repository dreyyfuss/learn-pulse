# LearnPulse

An event-driven online learning platform built as the **Moniepoint DreamDev Capstone (May 2026)** submission by **Anthony Alikah** and **John Agene**.

![Java](https://img.shields.io/badge/Java-21+-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-brightgreen?logo=springboot)
![Python](https://img.shields.io/badge/Python-3.12-blue?logo=python)
![React](https://img.shields.io/badge/React-Vite-61DAFB?logo=react)
![Kafka](https://img.shields.io/badge/Kafka-KRaft-black?logo=apachekafka)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)

**Live demo:** http://learnpulse.duckdns.org

---

## Overview

LearnPulse lets instructors publish structured courses (Course → Module → Lesson) and learners progress through them sequentially. On completion, the system generates a PDF certificate and emails it, guaranteed exactly once via Kafka idempotency.

Key features:

- **Structured courses** -- Course → Module → Lesson hierarchy with enforced sequential progression; learners cannot skip ahead
- **Quizzes** -- per-module MCQ and True/False quizzes with configurable passing scores and unlimited retakes; best attempt is always recorded
- **PDF certificates** -- generated and emailed to the learner on course completion, guaranteed exactly once via Kafka idempotency; downloadable from the dashboard at any time
- **Private courses** -- instructors can restrict enrolment to invited learners using a shareable enrolment code
- **Content upload** -- instructors upload video, documents (PDF, DOCX), or Markdown articles; learners access content through time-limited secure URLs
- **AI Course Builder** -- instructors describe a topic in plain text and the system generates a complete course (modules, lessons with full Markdown articles, and quizzes) through an async Kafka pipeline
- **AI Study Assistant** -- answers learner questions about course content using RAG (ChromaDB + Groq LLM), grounded strictly in that course's own material
- **Analytics** -- instructors get per-course enrolment and completion stats with a per-learner breakdown; admins get platform-wide visibility
- **Learning streaks** -- server-tracked daily streaks visible on the learner dashboard
- **Email notifications** -- learners are notified on enrolment and when the next module unlocks
- **Three roles** -- Learner, Instructor, and Admin; a single account can hold multiple roles simultaneously with a mode switcher in the nav bar

---

## Architecture

```
Browser
  └─► Traefik :80  (API gateway + JWT ForwardAuth + rate limiting)
        ├─► User Service     :8081  -- auth, users, admin
        ├─► Course Service   :8080  -- courses, enrolments, progress, analytics
        ├─► Cert Service     :8082  -- certificate generation and delivery
        ├─► AI Service       :9000  -- RAG chat (FastAPI + LangChain + Groq)
        └─► React SPA               -- learner and instructor modes
```

JWT validation is handled centrally by Traefik via ForwardAuth. Downstream services receive pre-validated `X-User-Id` and `X-User-Roles` headers and never touch raw tokens.

The async backbone is Apache Kafka (KRaft, no Zookeeper), carrying events for enrolment notifications, certificate generation, AI indexing, and course generation.

For the full architecture diagram, Kafka event payloads, API reference, and database schema see the [`docs/`](docs/) folder.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18 + Vite + React Router |
| API Gateway | Traefik v3 |
| Backend Services | Java 21 + Spring Boot 3 (x3) |
| AI Service | Python 3.12 + FastAPI + LangChain |
| Database | MySQL 8 (one schema per service) |
| Cache | Redis 7 |
| Message Broker | Apache Kafka (KRaft) |
| Vector Store | ChromaDB |
| LLM | Groq -- Llama 3.3 70B Versatile |
| Email | Mailgun |
| PDF Generation | Thymeleaf + Flying Saucer |
| Monitoring | Prometheus + Grafana |
| CI/CD | GitHub Actions |

---

## Running Locally

### Prerequisites

- Docker + Docker Compose v2 (v24+)
- Git

Java and Python are not needed -- everything runs in containers.

### 1. Clone the repository

```bash
git clone https://github.com/dreyyfuss/learn-pulse.git
cd learn-pulse
```

### 2. Set the Groq API key

The AI features (RAG chat, course generation, voice transcription) require a free Groq API key. Get one at [console.groq.com](https://console.groq.com) -- no credit card required.

```bash
cp apps/ai-service/.env.example apps/ai-service/.env
# Edit apps/ai-service/.env and set GROQ_API_KEY=<your-key>
```

The rest of the platform starts without this key; AI features will return errors if it is missing.

### 3. Start the stack

```bash
docker compose -f docker-compose.dev.yml up --build
```

The first build compiles three Spring Boot services -- allow 5 to 8 minutes. Subsequent starts use cached images and are much faster.

Wait until all three Spring Boot services log their ready message:

```
user-service    | Started UserServiceApplication
course-service  | Started CourseServiceApplication
cert-service    | Started CertServiceApplication
```

### 4. Open the app

Go to [http://localhost](http://localhost). A seeded admin account is created automatically by Flyway on first boot:

| Field | Value |
|---|---|
| Email | `admin@learnpulse.dev` |
| Password | `Admin@1234!` |

---

## Service URLs (local)

| URL | What it is |
|---|---|
| http://localhost | LearnPulse web app |
| http://localhost:8090/dashboard/ | Traefik dashboard |
| http://localhost:8085 | Kafka UI |
| http://localhost:3000 | Grafana (`admin` / `admin`) |
| http://localhost:9090 | Prometheus |

---

## Environment Variables

All services default to safe dev values. The only variable you need to set manually is `GROQ_API_KEY`.

| Variable | Required | Description |
|---|---|---|
| `GROQ_API_KEY` | Yes | AI chat, course generation, and transcription |
| `GROQ_LLM_MODEL` | No | Defaults to `llama-3.3-70b-versatile` |
| `MAILGUN_API_KEY` | No | Emails are silently skipped if blank |
| `MAILGUN_DOMAIN` | No | Defaults to `sandbox.mailgun.org` |
| `JWT_SECRET` | No | Change this in production |

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | Full product requirements, features, roles, and business rules |
| [`docs/decisions.md`](docs/decisions.md) | Key architectural decisions and the reasoning behind each |
| [`docs/api-spec.md`](docs/api-spec.md) | Complete REST API reference |
| [`docs/kafka-events.md`](docs/kafka-events.md) | Kafka topics, event payloads, and consumer contracts |
| [`docs/schema.md`](docs/schema.md) | Database schema for all services |
| [`docs/sequence-diagrams.md`](docs/sequence-diagrams.md) | Sequence diagrams for key flows (auth, enrolment, certificate generation, AI assistant) |
| [`docs/security.md`](docs/security.md) | Security model covering auth, file access, rate limiting, and idempotency |
| [`docs/perf.md`](docs/perf.md) | Caching strategy, database indexes, and performance considerations |
| [`docs/plan.md`](docs/plan.md) | Full delivery plan with phase breakdowns and acceptance criteria |
