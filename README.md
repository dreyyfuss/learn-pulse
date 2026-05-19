# LearnPulse

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

**Three roles:** Learner · Instructor · Admin — a single account can hold multiple roles simultaneously.

---

## 🏗️ Architecture

```
Browser
  └─► Traefik :80  (API gateway + JWT ForwardAuth + rate limiting)
        ├─► User Service     :8081  — auth, users, admin
        ├─► Course Service   :8080  — courses, enrolments, progress, analytics
        ├─► Cert Service     :8082  — certificate generation & delivery
        ├─► AI Service       :9000  — RAG chat (FastAPI + LangChain + Cerebras)
        └─► React SPA               — learner & instructor modes (/learn, /teach)

Async backbone: Apache Kafka (KRaft)
  course.published → AI Service (indexes lessons into ChromaDB)
  user.enrolled    → Email notification
  module.unlocked  → Email notification
  course.completed → Cert Service (generates PDF → uploads to MinIO)
  cert.generated   → Email delivery

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
| LLM | Cerebras Inference — Llama 3.1 8B |
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

The AI Study Assistant requires a free Cerebras API key. Sign up at [cloud.cerebras.ai](https://cloud.cerebras.ai) — no credit card required.

```bash
cp apps/ai-service/.env.example apps/ai-service/.env
# Set CEREBRAS_API_KEY=<your-key> in apps/ai-service/.env
```

> The rest of the platform starts fine without this key — only the AI chat tab will return an error.

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

## 🌐 Service URLs

| URL | Service |
|---|---|
| http://localhost | LearnPulse web app |
| http://localhost:8090/dashboard/ | Traefik dashboard |
| http://localhost:8085 | Kafka UI |
| http://localhost:9001 | MinIO console (`minioadmin` / `minioadmin`) |
| http://localhost:3000 | Grafana (`admin` / `admin`) |
| http://localhost:9090 | Prometheus |
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

All services default to safe dev values. The only variable you must set is `CEREBRAS_API_KEY` (see [Quick Start](#-quick-start)).

| Variable | Required | Default | Description |
|---|---|---|---|
| `CEREBRAS_API_KEY` | **Yes** | *(empty)* | AI chat responses — free tier at cloud.cerebras.ai |
| `GROQ_API_KEY` | **Yes** | *(empty)* | Audio/video transcription via Groq Whisper — free tier at console.groq.com |
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
| [`docs/PRD.md`](docs/PRD.md) | Full product requirements — features, roles, business rules |
| [`docs/api-spec.md`](docs/api-spec.md) | Complete REST API reference |
| [`docs/kafka-events.md`](docs/kafka-events.md) | Kafka topics, event payloads, consumer contracts |
| [`docs/ERD.md`](docs/ERD.md) | Entity-relationship diagram |
| [`docs/plan.md`](docs/plan.md) | Implementation plan and architectural decisions |