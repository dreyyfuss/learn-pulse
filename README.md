# LearnPulse

An online learning platform where instructors publish structured courses and learners progress through them sequentially, earning PDF certificates on completion. Includes an AI Study Assistant backed by RAG (Retrieval-Augmented Generation).

**Moniepoint DreamDev Capstone — May 2026**

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        React Frontend                            │
│   Learner Mode (/learn/*)  │  Instructor Mode (/teach/*)        │
│                Role Switcher in Navbar                           │
└──────────────────────────┬──────────────────────────────────────┘
                           │ REST / JSON  :80
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                     Traefik API Gateway                           │
│  /api/auth/*  /api/users/*  /api/admin/users/*                   │
│      → User Service :8081                                         │
│  /api/learner/certificates  /api/certificates/*                   │
│      → Certificate Service :8082                                  │
│  /api/* (all other)  → Course Service :8080                       │
│  / (catch-all)       → React SPA (static)                        │
│                                                                   │
│  Rate-limit middleware on /api/auth/*                             │
│  (10 req/min, burst 5 — per client IP)                           │
└────┬──────────────────────────┬───────────────────┬─────────────┘
     │                          │                   │
     ▼                          ▼                   ▼
┌─────────────────┐   ┌──────────────────┐   ┌────────────────────┐
│  User Service    │   │  Course Service  │   │ Certificate Service │
│  (Spring Boot)   │   │  (Spring Boot)   │   │  (Spring Boot)      │
│                  │   │                  │   │                     │
│  Auth | Users    │   │  Courses         │   │  CertificateConsumer│
│  Admin user mgmt │   │  Enrolments      │   │  (group: cert-svc)  │
│  JWT issuance    │   │  Progress        │   │                     │
│                  │   │  Analytics       │   │  → Thymeleaf + PDF  │
└────────┬─────────┘   │  EmailConsumer   │   │  → S3 upload        │
         │             │                  │   │  → cert.generated   │
         ▼             │  Kafka Producers:│   │    (outbox)         │
┌─────────────────┐    │  course.published│   └──────────┬──────────┘
│  User DB        │    │  user.enrolled   │              │
│  (MySQL)        │    │  module.unlocked │              ▼
│  users          │    │  course.completed│   ┌────────────────────┐
│  user_roles     │    │                  │   │  Cert DB (MySQL)    │
└─────────────────┘    │  REST calls:     │   │  certificates       │
                        │  POST /ai/chat   │   │  idempotency_log    │
                        └────────┬─────────┘   └────────────────────┘
                                 │
                        ┌────────▼─────────┐
                        │   LMS DB (MySQL)  │
                        │   courses         │
                        │   modules         │
                        │   lessons         │
                        │   enrolments      │
                        │   lesson_progress │
                        │   module_unlocks  │
                        │   idempotency_log │
                        └──────────────────┘
       │                              │
       ▼ (Kafka)                      ▼ (REST — internal)
┌────────────────────┐   ┌────────────────────────────┐
│   Apache Kafka      │   │   FastAPI AI Service        │
│                     │   │   (Python / LangChain)      │
│  course.published ──┼───►  aiokafka consumer          │
│  user.enrolled      │   │  → embeds & indexes lessons │
│  module.unlocked    │   └────────────┬───────────────┘
│  course.completed   │                │
│  cert.generated     │   ┌────────────▼───────────────┐
└─────────────────────┘   │  ChromaDB                   │
                           │  (Vector Store)             │
                           └────────────────────────────┘

                ┌──────────────────┐    ┌────────────────┐
                │     MinIO / S3    │    │    Mailgun      │
                │  cert PDFs        │    │  (Email)        │
                │  lesson assets    │    └────────────────┘
                └──────────────────┘
```

### Traefik routing

| Path prefix | Service | Middleware |
|---|---|---|
| `/api/auth/*` | User Service :8081 | Rate-limit (10 rpm, burst 5) |
| `/api/users/*`, `/api/admin/users/*` | User Service :8081 | JWT ForwardAuth |
| `/api/learner/certificates`, `/api/certificates/*` | Certificate Service :8082 | JWT ForwardAuth |
| `/api/*` (catch-all) | Course Service :8080 | JWT ForwardAuth |
| `/` (catch-all) | React SPA | — |

JWT validation is handled by Traefik calling `User Service /api/auth/validate` via ForwardAuth — downstream services receive pre-validated `X-User-Id`, `X-User-Email`, `X-User-Roles` headers and never see raw JWTs.

---

## Prerequisites

| Tool | Version | Required for |
|---|---|---|
| Docker + Docker Compose | 24+ / v2 | Running the full stack |
| Git | any | Clone |
| k6 | 0.50+ | Rate-limit verification (optional) |
| Java 17 + Maven | — | Local Spring Boot dev only (skip with Docker) |
| Python 3.12 + pip | — | Local AI service dev only (skip with Docker) |

---

## Quick start

> Target: browser showing the LearnPulse homepage in under 15 minutes.

**1. Clone**

```bash
git clone https://github.com/dreyyfuss/learn-pulse.git
cd learn-pulse
```

**2. Set the one required secret**

The AI Study Assistant needs a Cerebras API key (free tier — sign up at [cloud.cerebras.ai](https://cloud.cerebras.ai)).
All other dev secrets have safe defaults.

```bash
cp apps/ai-service/.env.example apps/ai-service/.env
# Open apps/ai-service/.env and set CEREBRAS_API_KEY=<your-key>
```

> If you skip this step the rest of the platform works fine — only the AI chat tab will error.

**3. Start the full stack**

```bash
docker compose -f docker-compose.dev.yml up --build
```

First build downloads base images and compiles all three Spring Boot services — allow 5–8 minutes. Subsequent starts are fast (images are cached).

Wait until you see health-check lines like:

```
user-service    | Started UserServiceApplication
course-service  | Started CourseServiceApplication
cert-service    | Started CertServiceApplication
```

**4. Seed the admin account**

Flyway migrations run automatically on first boot and create a seeded admin:

| Field | Value |
|---|---|
| Email | `admin@learnpulse.dev` |
| Password | `Admin@1234!` |

Log in at http://localhost with these credentials and select **Admin** mode.

**5. Open the app**

| URL | What you see |
|---|---|
| http://localhost | LearnPulse web app |
| http://localhost:8090/dashboard/ | Traefik dashboard |
| http://localhost:8085 | Kafka UI |
| http://localhost:9001 | MinIO console (minioadmin / minioadmin) |

**6. Demo the golden path**

1. Register an instructor account → create a course with one module and one lesson → publish it.
2. Register a learner account → find the course → enrol → start it → mark the lesson complete.
3. Check Kafka UI — you should see messages on `user.enrolled`, `module.unlocked`, and `course.completed`.
4. Check MinIO console (`learnpulse` bucket) — the certificate PDF should appear under `certificates/`.

---

## Port map

| Service | Host port | Notes |
|---|---|---|
| **Traefik (web)** | **80** | Primary entry point for all traffic |
| Traefik dashboard | 8090 | http://localhost:8090/dashboard/ |
| User Service | 8081 | Direct access, bypasses Traefik |
| Course Service | 8080 | Direct access, bypasses Traefik |
| Certificate Service | 8082 | Direct access, bypasses Traefik |
| MySQL | 3307 | `learnpulse` / `learnpulse` |
| Redis | 6379 | No password in dev |
| Kafka (external) | 9094 | For host-side tools; containers use `kafka:9092` |
| Kafka UI | 8085 | http://localhost:8085 |
| MinIO S3 API | 9010 | `S3_ENDPOINT=http://localhost:9010` |
| MinIO Console | 9001 | http://localhost:9001 (minioadmin / minioadmin) |
| AI Service | 9000 | Run separately (see below) |

---

## Kafka topics

| Topic | Producer | Consumer | Trigger |
|---|---|---|---|
| `course.published` | Course Service | FastAPI AI Service | Instructor publishes a course |
| `user.enrolled` | Course Service | User Service (email) | Learner enrols |
| `module.unlocked` | Course Service | User Service (email) | System unlocks next module |
| `course.completed` | Course Service | Certificate Service | Learner completes all lessons |
| `certificate.generated` | Certificate Service | Course Service (email) | PDF uploaded to S3 |

All topics have a corresponding `.DLT` dead-letter topic. Messages that fail after retries are routed there automatically.

---

## Running the AI service

The AI service is not included in `docker-compose.dev.yml` and runs as a standalone container or process.

**Option A — Docker**

```bash
docker build -t learnpulse-ai apps/ai-service
docker run --rm \
  --network learn-pulse_learnpulse \
  --env-file apps/ai-service/.env \
  -p 9000:9000 \
  learnpulse-ai
```

**Option B — Local Python**

```bash
cd apps/ai-service
pip install -r requirements.txt
cp .env.example .env   # fill in CEREBRAS_API_KEY
uvicorn app.main:app --host 0.0.0.0 --port 9000 --reload
```

Health check: `curl http://localhost:9000/healthz`

---

## Rate-limit verification (optional)

Requires [k6](https://k6.io/docs/getting-started/installation/).

```bash
k6 run infrastructure/traefik/ratelimit-test.js
```

The script hammers `POST /api/auth/login` with 15 virtual users for 10 seconds. The threshold `rate_limited > 0` must pass — confirming Traefik returns `429` responses before requests reach the User Service.

> The test URL inside the script is `http://traefik/api/auth/login` (Docker-internal hostname). If running k6 from your host machine, edit the URL to `http://localhost/api/auth/login` first.

---

## Environment variables

### Spring Boot services

All Spring Boot services default to the docker-compose dev values. Override via environment variables in `docker-compose.dev.yml` or export before running locally.

| Variable | Default (dev) | Description |
|---|---|---|
| `JWT_SECRET` | `learnpulse-user-svc-dev-only-secret-key-not-for-prod-use` | HS256 signing key — **change in production** |
| `MAILGUN_API_KEY` | *(empty)* | Mailgun API key — emails are silently skipped if blank |
| `MAILGUN_DOMAIN` | `sandbox.mailgun.org` | Mailgun sending domain |
| `MAILGUN_FROM` | `noreply@learnpulse.dev` | From address |
| `DB_URL` | `jdbc:mysql://mysql:3306/...` | MySQL JDBC URL |
| `DB_USERNAME` | `learnpulse` | MySQL username |
| `DB_PASSWORD` | `learnpulse` | MySQL password |

### AI service (`apps/ai-service/.env`)

| Variable | Required | Description |
|---|---|---|
| `CEREBRAS_API_KEY` | **Yes** | Cerebras inference API key ([cloud.cerebras.ai](https://cloud.cerebras.ai)) |
| `CEREBRAS_MODEL` | No (default: `llama-3.3-70b`) | Model to use |
| `KAFKA_BOOTSTRAP_SERVERS` | No (default: `kafka:9092`) | Use `localhost:9094` for local dev outside Docker |
| `CHROMA_HOST` | No (default: `chromadb`) | ChromaDB host |

---

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React + Vite + React Router |
| API Gateway | Traefik v3 |
| Course / LMS Service | Java 17 + Spring Boot 3 |
| User Service | Java 17 + Spring Boot 3 |
| Certificate Service | Java 17 + Spring Boot 3 |
| AI Service | Python 3.12 + FastAPI + LangChain |
| Databases | MySQL 8 (one schema per service) |
| Cache | Redis 7 |
| Message Broker | Apache Kafka (KRaft, no Zookeeper) |
| Object Storage | MinIO (dev) / AWS S3 (prod) |
| Email | Mailgun |
| PDF Generation | Thymeleaf + Flying Saucer |
| Vector Store | ChromaDB |
| LLM | Cerebras Inference (Llama 3.3 70B) |
