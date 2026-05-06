# LearnPulse — Product Requirements Document
**Version:** 1.0  
**Programme:** Moniepoint DreamDev Capstone  
**Status:** Draft  
**Last Updated:** May 2026

---

## Table of Contents
1. [Overview](#1-overview)
2. [Goals & Non-Goals](#2-goals--non-goals)
3. [Actors & Roles](#3-actors--roles)
4. [Core Entities & Data Model](#4-core-entities--data-model)
5. [Feature Specifications](#5-feature-specifications)
6. [Event-Driven Architecture](#6-event-driven-architecture)
7. [AI Study Assistant](#7-ai-study-assistant)
8. [System Architecture](#8-system-architecture)
9. [API Design](#9-api-design)
10. [Non-Functional Requirements](#10-non-functional-requirements)
11. [Tech Stack Summary](#11-tech-stack-summary)

---

## 1. Overview

LearnPulse is an online learning platform that enables instructors to create and publish structured courses, and learners to enrol in and progress through those courses at their own pace. The platform enforces sequential lesson and module completion, automatically unlocking the next module when all lessons in the current one are finished. Upon completing an entire course, the system generates a digital certificate and emails it to the learner — with a strict exactly-once delivery guarantee powered by Apache Kafka.

An AI-powered Study Assistant, built as a separate FastAPI microservice, provides per-course RAG (Retrieval-Augmented Generation) chat support, allowing learners to ask questions about course content in real time.

---

## 2. Goals & Non-Goals

### Goals
- Allow instructors to self-register and create/publish structured courses (Course → Module → Lesson).
- Allow learners to self-enrol in public courses or access private courses via an enrolment code.
- Enforce strictly sequential lesson and module progression.
- Generate and deliver a PDF certificate exactly once per learner per completed course.
- Provide instructors with per-learner and aggregate analytics on their courses.
- Provide admins with platform-wide management capabilities.
- Offer a per-course AI Study Assistant powered by RAG.

### Non-Goals
- Payments and billing (out of scope).
- Native mobile applications (web only).
- File storage/upload for lesson content (only the URL/metadata is stored; actual files are managed externally in S3).
- Updating a course after a learner has started it.
- Sub-module hierarchies or lessons that exist outside a module.

---

## 3. Actors & Roles

| Actor | Description |
|---|---|
| **Learner** | Enrolled in courses, progresses through lessons, earns certificates. |
| **Instructor** | Creates and manages courses, modules, and lessons; views analytics. |
| **Admin** | Manages platform users and content; tracks enrolment and completion rates. |
| **System** | Automated actor; triggers certificate generation via Kafka on course completion. |

### Role Rules
- A **single user account** can hold multiple roles simultaneously (e.g., a user can be both Instructor and Learner).
- Instructors self-register and receive instructor privileges immediately upon registration — no admin approval required.
- The first Admin account is seeded into the database. Subsequent admins are promoted by an existing admin.
- There is no Super Admin — Admin is a flat, single-tier role.

---

## 4. Core Entities & Data Model

### 4.1 Entity Overview

```
User
 ├── roles: [LEARNER, INSTRUCTOR, ADMIN]  (multi-role, single account)
 ├── Enrolments  →  Course
 ├── LessonProgress  →  Lesson
 └── Certificates  →  Course

Course
 ├── Instructor (User)
 ├── visibility: PUBLIC | PRIVATE
 ├── enrolmentCode (auto-generated for private courses)
 ├── status: DRAFT | PUBLISHED | LOCKED (locked once a learner starts)
 └── Modules (ordered)
      └── Lessons (ordered)

Enrolment
 ├── User (Learner)
 ├── Course
 ├── status: ACTIVE | COMPLETED
 └── startedAt (set when learner explicitly clicks "Start Course")

LessonProgress
 ├── User
 ├── Lesson
 ├── status: NOT_STARTED | COMPLETED
 └── completedAt

Certificate
 ├── User
 ├── Course
 ├── certificateId (UUID)
 ├── s3Url
 ├── issuedAt
 └── UNIQUE CONSTRAINT on (user_id, course_id)
```

### 4.2 Key Schema Notes

- `Module.orderIndex` and `Lesson.orderIndex` enforce sequential ordering.
- `Course.status` transitions: `DRAFT → PUBLISHED`. Once any learner's enrolment has `startedAt` set, the course is considered **locked** — instructors can no longer add, remove, or edit modules or lessons.
- `Enrolment.status` transitions: `ACTIVE → COMPLETED`. Completion is triggered when all lessons across all modules are marked complete.
- `LessonProgress.status` is **irreversible** — once set to `COMPLETED`, it cannot be reverted.
- A `Certificate` row is written only once per `(user_id, course_id)` pair, enforced at both the database level (unique constraint) and the Kafka consumer level (idempotency key).

---

## 5. Feature Specifications

### 5.1 Authentication & User Management

#### Registration
- **Learner registration:** User signs up with name, email, and password. Role defaults to `LEARNER`.
- **Instructor registration:** User signs up and selects "Register as Instructor." Role is set to `[LEARNER, INSTRUCTOR]` immediately — no approval step.
- Passwords are hashed using BCrypt.
- JWT tokens are issued on login. Tokens carry the user's ID and their full role list.
- A single token grants access to all roles the user holds. Role-based route guards enforce permissions on the backend.

#### Admin Promotion
- An existing Admin can promote any user to `ADMIN` via the admin dashboard.
- The first Admin is seeded via a database migration script.

#### Account Suspension
- Admins can suspend any user account.
- Suspended users cannot log in and receive a `403 Forbidden` with a descriptive message.

---

### 5.2 Course Management (Instructor)

#### Course Creation
An instructor creates a course by providing:
- Title, description, thumbnail URL (stored as metadata, file hosted on S3), category/tags.
- Visibility: `PUBLIC` or `PRIVATE`.
  - If `PRIVATE`, the system auto-generates a unique alphanumeric enrolment code at creation time. The code is permanent (does not expire) and is a single shared code for the course (not per-invitee).

#### Course Locking Rule
- A course can be freely edited (modules/lessons added, reordered, updated) while in `DRAFT` or `PUBLISHED` state and no learner has started it.
- **Once any learner clicks "Start Course," the course is locked.** The instructor will see a read-only view of the course structure and cannot make any structural or content changes.
- This rule guarantees consistency of `LessonProgress` and module-unlock state for all active learners.

#### Module & Lesson Management (pre-lock only)
- Modules are created within a course and given an order index.
- Lessons are created within a module and given an order index.
- Each lesson contains: title, description, content type (video, document, article, etc.), and an external URL pointing to the actual file hosted in S3 or any external source.
- Additional file attachments per lesson are supported — only the S3 URL is stored in the database.

#### Course Publishing
- A course must have at least one module, and each module must have at least one lesson, before it can be published.
- Instructors explicitly publish a course (transitions from `DRAFT` to `PUBLISHED`).
- Only published courses are visible to learners.

---

### 5.3 Enrolment & Access

#### Public Courses
- Any authenticated learner can enrol in a public course with a single click.
- The `Enrolment` record is created with `status: ACTIVE` and `startedAt: null`.

#### Private Courses
- Private courses appear in search results with a **"Request Access"** CTA instead of an enrol button.
- Clicking "Request Access" opens a modal prompting the learner to enter the enrolment code.
- If the code matches, the learner is immediately enrolled (same flow as public enrolment).
- If the code is incorrect, an error message is shown in the modal.

#### Starting a Course
- Enrolment alone does not unlock any content.
- The learner must explicitly click **"Start Course"** from their dashboard or the course page.
- This action sets `Enrolment.startedAt` to the current timestamp and locks the course for future edits.
- Module 1 is unlocked at this point.

#### Unenrolment
- Admins can manually unenrol a learner from any course.
- Learners cannot self-unenrol (by design — keeps progress records clean).

---

### 5.4 Learning Experience (Learner)

#### Lesson Progression
- Within an unlocked module, lessons must be completed in sequential order (by `orderIndex`).
- A learner cannot mark Lesson N+1 as complete until Lesson N is marked complete.
- Marking a lesson complete is **instant and irreversible** — the `LessonProgress` record is created with `status: COMPLETED` and a `completedAt` timestamp.

#### Module Unlocking
- When the last lesson in a module is marked complete, the system automatically unlocks the next module (increments to the next `orderIndex`).
- The final module has no "next" — completing its last lesson triggers the course completion flow.

#### Course Completion Flow
1. Learner marks the final lesson of the final module as complete.
2. Backend sets `Enrolment.status = COMPLETED`.
3. Backend publishes a `COURSE_COMPLETED` event to the Kafka topic `course.completed`.
4. The Kafka consumer processes the event and generates the certificate (see Section 6).

---

### 5.5 Analytics (Instructor)

Instructors can view analytics for each of their courses. Analytics are scoped to courses the instructor owns.

#### Aggregate Metrics (per course)
- Total enrolments
- Total completions
- Completion rate (%)
- Number of learners currently active (enrolled but not completed)

#### Per-Learner Breakdown (per course)
- List of enrolled learners (name, email)
- Each learner's current status: not started / in progress / completed
- Current lesson the learner is on (most recent unlocked, incomplete lesson)
- Date enrolled, date started, date completed (if applicable)

---

### 5.6 Admin Dashboard

Admins have a dedicated dashboard with the following capabilities:

#### User Management
- View all users with their roles, registration date, and account status.
- Promote any user to Admin.
- Suspend or reinstate any user account.

#### Course Management
- View all courses on the platform (public and private).
- Delete any course (with a confirmation step). Deleting a course also deletes all associated modules, lessons, enrolments, progress records, and certificates.

#### Enrolment Management
- Manually enrol a learner into any course.
- Manually unenrol a learner from any course.

#### Platform Analytics
- Total number of users (by role).
- Total number of courses (by status).
- Platform-wide enrolment count.
- Platform-wide completion count and rate.

---

### 5.7 Instructor / Learner Mode Switching

Users who hold both the `INSTRUCTOR` and `LEARNER` roles access two distinct UI contexts within the same account. Mode switching is handled via a **role context switcher** in the top navigation bar — a clearly labelled toggle or dropdown (e.g. "Switch to Teaching" / "Switch to Learning").

#### Route Namespaces
The two modes map to completely separate route namespaces in React:

| Mode | Route Prefix | Primary Pages |
|---|---|---|
| **Learner Mode** | `/learn/...` | My Courses, Course Player, My Certificates, AI Chat |
| **Instructor Mode** | `/teach/...` | My Courses (management), Create Course, Analytics, Course Builder |

#### Behaviour
- The active mode is stored in the React app's global state (e.g. a context or Zustand store) and reflected in the URL.
- Switching modes navigates the user to the dashboard of the selected mode (`/learn/dashboard` or `/teach/dashboard`).
- The switcher is only visible to users who hold both roles. Pure learners never see the toggle.
- JWT tokens carry both roles. The backend does not care which "mode" the frontend is in — it enforces role-based permissions on every API call independently.
- A user in Learner Mode can still navigate to `/teach/...` routes directly — the backend will permit it if they have the Instructor role. The mode switch is a UX convention, not a security boundary.

---

- Learners can view all certificates they have earned from their dashboard.
- Each certificate entry shows: course name, completion date, and a **Download** button.
- The download button triggers a signed S3 URL download of the PDF certificate.
- Certificates contain: learner full name, course name, instructor full name, completion date, certificate UUID, and the LearnPulse platform logo.

---

## 6. Event-Driven Architecture

LearnPulse uses Apache Kafka as its single async backbone. The table below summarises all topics, their producers, and their consumers.

| Topic | Producer | Consumer | Trigger |
|---|---|---|---|
| `course.published` | Spring Boot | FastAPI AI Service | Instructor publishes a course |
| `user.enrolled` | Spring Boot | Spring Boot (Email Consumer) | Learner enrols in a course |
| `module.unlocked` | Spring Boot | Spring Boot (Email Consumer) | System unlocks a new module for a learner |
| `course.completed` | Spring Boot | Certificate Service (Spring Boot — separate app) | Learner completes all lessons |
| `certificate.generated` | Certificate Service | Course Service (Email Consumer) | Certificate PDF successfully uploaded to S3 |

---

### 6.1 Topic: `course.published`

**Trigger:** Instructor clicks "Publish Course."

**Producer:** Spring Boot backend.

**Consumer:** FastAPI AI Service (via `aiokafka`). The FastAPI service operates as an independent Kafka consumer — Spring Boot does not call it directly. This keeps the AI service fully autonomous and decoupled.

**Event Payload:**
```json
{
  "eventId": "uuid-v4",
  "courseId": 456,
  "publishedAt": "2026-05-01T10:00:00Z",
  "lessons": [
    {
      "lessonId": 1,
      "title": "Introduction to REST APIs",
      "description": "...",
      "moduleTitle": "Module 1: Foundations",
      "moduleDescription": "..."
    }
  ]
}
```

**Processing (FastAPI):**
The consumer embeds each lesson's content, stores the vectors in ChromaDB under the `courseId` namespace, and marks the course as indexed. Because a course is locked once any learner starts it, the knowledge base is written once and never needs re-indexing.

---

### 6.2 Topic: `user.enrolled`

**Trigger:** Learner successfully enrols in a course (public or via enrolment code).

**Producer:** Spring Boot backend.

**Consumer:** Spring Boot email consumer → sends a welcome email via Mailgun.

**Event Payload:**
```json
{
  "eventId": "uuid-v4",
  "userId": 123,
  "courseId": 456,
  "enrolledAt": "2026-05-01T11:00:00Z"
}
```

**Email content:** Learner's name, course title, instructor name, and a direct link to the course page. This decouples email delivery from the enrolment API response — the enrolment endpoint returns immediately.

---

### 6.3 Topic: `module.unlocked`

**Trigger:** Learner marks the last lesson in a module as complete, automatically unlocking the next module.

**Producer:** Spring Boot backend (inside the lesson-completion handler, after unlock logic runs).

**Consumer:** Spring Boot email consumer → sends a "Your next module is ready" email via Mailgun.

**Event Payload:**
```json
{
  "eventId": "uuid-v4",
  "userId": 123,
  "courseId": 456,
  "unlockedModuleId": 789,
  "unlockedModuleTitle": "Module 2: Advanced Concepts",
  "unlockedAt": "2026-05-01T13:00:00Z"
}
```

**Note:** This event is NOT published when the final module is completed — that scenario publishes `course.completed` instead.

---

### 6.4 Topic: `course.completed`

**Trigger:** Learner marks the final lesson of the final module as complete. Backend sets `Enrolment.status = COMPLETED`.

**Producer:** Spring Boot backend.

**Consumer:** Certificate Service `CertificateConsumer` (separate Spring Boot application).

**Event Payload:**
```json
{
  "eventId": "uuid-v4",
  "userId": 123,
  "courseId": 456,
  "enrolmentId": 789,
  "completedAt": "2026-05-01T14:00:00Z"
}
```

#### Exactly-Once Certificate Guarantee

The system employs a two-layer defence to ensure a certificate is issued exactly once per learner per course, even under consumer restarts, retries, or duplicate Kafka messages.

**Layer 1 — Database Unique Constraint:**
The `certificates` table has a unique constraint on `(user_id, course_id)`. Any duplicate insert throws a `DataIntegrityViolationException`, which the consumer catches and treats as a no-op.

**Layer 2 — Kafka Consumer Idempotency Key:**
Before processing, the consumer checks an `idempotency_log` table for the `eventId`. If found, the message is acknowledged and skipped. If not found, the consumer processes the event and writes the `eventId` to the log within the same DB transaction as the certificate insert.

**Processing Flow:**
```
course.completed message received
        │
        ▼
Check idempotency_log for eventId
        │
   ┌────┴────┐
  Found    Not Found
   │           │
  ACK       Begin DB Transaction
  Skip          │
            Generate PDF (Thymeleaf + Flying Saucer)
                │
            Upload PDF to S3
            → path: certificates/{userId}/{courseId}/{certId}.pdf
                │
            INSERT into certificates (user_id, course_id, s3_url, ...)
                │
            INSERT into idempotency_log (event_id)
                │
            COMMIT Transaction
                │
            Publish → certificate.generated (Kafka)
                │
            ACK message
```

If the transaction fails before COMMIT, the message is not acknowledged and Kafka redelivers it safely.

---

### 6.5 Topic: `certificate.generated`

**Trigger:** Certificate Service consumer successfully uploads the PDF to S3 and commits the DB transaction.

**Producer:** Certificate Service `CertificateConsumer`.

**Consumer:** Course Service email consumer → sends the certificate delivery email via Mailgun.

**Event Payload:**
```json
{
  "eventId": "uuid-v4",
  "userId": 123,
  "courseId": 456,
  "certificateId": "cert-uuid",
  "s3Url": "https://s3.amazonaws.com/...",
  "issuedAt": "2026-05-01T14:05:00Z"
}
```

**Why a separate topic?** Decoupling PDF generation from email delivery makes each step independently retriable. If Mailgun is temporarily unavailable, the certificate is already safely stored and the email consumer retries without re-generating the PDF.

**Email content:** Congratulatory message, course name, and a download link to the certificate PDF.

---

### 6.6 Certificate PDF Generation

- **Template engine:** Thymeleaf (HTML template) + Flying Saucer (HTML → PDF renderer).
- **Template data:** Learner full name, course name, instructor full name, completion date, certificate UUID, platform logo (embedded as base64 or referenced from a static S3 URL).
- **Storage path:** `certificates/{userId}/{courseId}/{certificateId}.pdf` in S3.
- **DB field:** `Certificate.s3Url` stores the S3 object path; the API generates a pre-signed URL on demand for downloads.

---

## 7. AI Study Assistant

### 7.1 Overview

A separate **FastAPI** microservice provides a per-course AI Study Assistant. Learners who are enrolled in and have started a course can open a chat panel within the course interface and ask questions about the course content.

The assistant answers only from that specific course's content — it will not hallucinate information outside of what is indexed for the course.

### 7.2 Tech Stack
- **FastAPI** (Python) — REST API for the chat interface.
- **LangChain** — RAG orchestration.
- **Cerebras Inference API** — LLM for response generation, via the `langchain-cerebras` package (`ChatCerebras`). Recommended model: **Llama 3.3 70B** (best balance of quality and speed on the free tier). No credit card required — sign up at `cloud.cerebras.ai`.
- **ChromaDB** (or Pinecone free tier) — Vector store for lesson embeddings.

#### Cerebras Free Tier Limits
| Limit | Value |
|---|---|
| Tokens per day | 1,000,000 |
| Requests per minute | 30 RPM |
| Tokens per minute | 60,000 TPM |

These limits are sufficient for a capstone/prototype with real users. If limits are hit in production, upgrading to Cerebras's paid Developer Tier is the natural next step — no code changes required, only the API key tier changes.

#### LangChain Integration
Cerebras plugs directly into LangChain's standard interface, making the RAG chain a minimal code change from any other LLM provider:
```python
from langchain_cerebras import ChatCerebras

llm = ChatCerebras(
    model="llama-3.3-70b",
    api_key=os.getenv("CEREBRAS_API_KEY"),
)
```
The rest of the RAG pipeline (prompt templates, vector retrieval, chat history) is provider-agnostic LangChain code.

### 7.3 Knowledge Base Construction

Knowledge base indexing is triggered by the `course.published` Kafka event (see Section 6.1). The FastAPI service runs its own Kafka consumer using `aiokafka` — Spring Boot does not call FastAPI directly. This makes the AI service fully autonomous and independently deployable.

**What gets indexed per lesson:**
- Lesson title
- Lesson description
- Content type and any descriptive metadata
- Module title and module description

Each lesson's content is chunked, embedded, and stored in the vector store with `course_id` as the namespace/filter key.

**Re-indexing:** Since a course is locked once a learner starts it, the knowledge base is written once on publish and never needs re-indexing.

### 7.4 RAG Chat Flow

```
Learner sends message: "What is covered in Module 2?"
        │
        ▼
FastAPI receives request with {courseId, userId, message, chatHistory}
        │
        ▼
Query vector store filtered by courseId
        │
        ▼
Retrieve top-K relevant lesson chunks
        │
        ▼
Build prompt: system context + retrieved chunks + chat history + user message
        │
        ▼
LLM generates response
        │
        ▼
Return response with source lesson references
```

### 7.5 API Contract (FastAPI ↔ Spring Boot)

Since indexing is event-driven via Kafka, the only synchronous API between Spring Boot and FastAPI is the chat endpoint. Spring Boot calls this on behalf of the learner after validating their enrolment.

**Chat:**
```
POST /ai/courses/{courseId}/chat
Body: { userId, message, chatHistory: [{role, content}] }
Response: { reply, sourceLessons: [{ lessonId, title }] }
```

### 7.6 Access Control
- Only learners with an active enrolment for the given `courseId` can call the chat endpoint.
- Spring Boot validates enrolment and passes a signed request or a shared secret to the FastAPI service.

---

## 8. System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        React Frontend                            │
│   Learner Mode (/learn/*)  │  Instructor Mode (/teach/*)        │
│                Role Switcher in Navbar                           │
└──────────────────────────┬──────────────────────────────────────┘
                           │ REST / JSON  :80 / :443
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                     Traefik API Gateway                           │
│  /api/auth/*  /api/users/*  /api/admin/users/*                   │
│      → User Service :8081                                         │
│  /api/learner/certificates  /api/certificates/*                   │
│      → Certificate Service :8082                                  │
│  /api/* (all other)  → Course Service :8080                         │
│  / (catch-all)       → React SPA (static)                        │
│                                                                   │
│  Rate-limit middleware on /api/auth/login and /api/auth/register  │
│  (10 rpm, burst 5 — applied per client IP)                        │
└────┬──────────────────────────┬───────────────────┬─────────────┘
     │                          │                   │
     ▼                          ▼                   ▼
┌─────────────────┐   ┌──────────────────┐   ┌────────────────────┐
│  User Service    │   │   Course Service    │   │ Certificate Service │
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
│  User DB         │    │  user.enrolled   │              │
│  (MySQL)         │    │  module.unlocked │              ▼
│  users           │    │  course.completed│   ┌────────────────────┐
│  user_roles      │    │                  │   │  Cert DB (MySQL)    │
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
└─────────────────────┘   │  ChromaDB / Pinecone        │
                           │  (Vector Store)             │
                           └────────────────────────────┘

                ┌──────────────────┐    ┌────────────────┐
                │     AWS S3        │    │    Mailgun      │
                │  cert PDFs        │    │  (Email)        │
                │  lesson assets    │    └────────────────┘
                └──────────────────┘
```

---

## 9. API Design

All API responses follow the envelope format:
```json
{
  "status": "success | error",
  "data": { },
  "message": "Human-readable description"
}
```

### 9.1 Auth
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register as learner or instructor |
| POST | `/api/auth/login` | Login, returns JWT |
| POST | `/api/auth/refresh` | Refresh JWT |

### 9.2 Users
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/api/users/me` | Get own profile | Any |
| PATCH | `/api/users/me` | Update own profile | Any |
| GET | `/api/admin/users` | List all users | Admin |
| PATCH | `/api/admin/users/{id}/promote` | Promote user to Admin | Admin |
| PATCH | `/api/admin/users/{id}/suspend` | Suspend user | Admin |
| PATCH | `/api/admin/users/{id}/reinstate` | Reinstate user | Admin |

### 9.3 Courses
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/api/courses` | List published courses (search/filter) | Any |
| GET | `/api/courses/{id}` | Get course details | Any |
| POST | `/api/courses` | Create course | Instructor |
| PATCH | `/api/courses/{id}` | Update course (pre-lock only) | Instructor (owner) |
| POST | `/api/courses/{id}/publish` | Publish course | Instructor (owner) |
| DELETE | `/api/courses/{id}` | Delete course | Admin |
| GET | `/api/instructor/courses` | Get instructor's own courses | Instructor |

### 9.4 Modules & Lessons
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | `/api/courses/{courseId}/modules` | Create module | Instructor (owner) |
| PATCH | `/api/courses/{courseId}/modules/{id}` | Update module | Instructor (owner) |
| DELETE | `/api/courses/{courseId}/modules/{id}` | Delete module | Instructor (owner) |
| POST | `/api/courses/{courseId}/modules/{moduleId}/lessons` | Create lesson | Instructor (owner) |
| PATCH | `/api/courses/{courseId}/modules/{moduleId}/lessons/{id}` | Update lesson | Instructor (owner) |
| DELETE | `/api/courses/{courseId}/modules/{moduleId}/lessons/{id}` | Delete lesson | Instructor (owner) |

### 9.5 Enrolments
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | `/api/enrolments` | Enrol in a course (body: `{ courseId, enrolmentCode? }`) | Learner |
| POST | `/api/enrolments/{id}/start` | Start a course | Learner |
| GET | `/api/learner/enrolments` | Get own enrolments | Learner |
| POST | `/api/admin/enrolments` | Manually enrol a learner | Admin |
| DELETE | `/api/admin/enrolments/{id}` | Manually unenrol a learner | Admin |

### 9.6 Progress
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | `/api/lessons/{lessonId}/complete` | Mark lesson as complete | Learner |
| GET | `/api/enrolments/{id}/progress` | Get progress for an enrolment | Learner / Instructor (owner) |

### 9.7 Certificates
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/api/learner/certificates` | Get own certificates | Learner |
| GET | `/api/certificates/{id}/download` | Download certificate PDF (redirect to signed S3 URL) | Learner (owner) |

### 9.8 Analytics
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/api/instructor/courses/{id}/analytics` | Aggregate + per-learner analytics | Instructor (owner) |
| GET | `/api/admin/analytics` | Platform-wide analytics | Admin |

---

## 10. Non-Functional Requirements

### Performance
- API response time for standard CRUD operations: < 300ms (p95).
- Certificate generation (Kafka consumer to email sent): < 30 seconds.
- AI Study Assistant response latency: < 5 seconds (p95).

### Reliability
- Certificate exactly-once guarantee is enforced at both DB and Kafka consumer levels (see Section 6.2).
- All Kafka consumer failures must not acknowledge the message — Kafka will redeliver.
- The `idempotency_log` insert and `certificates` insert must be in the same DB transaction.

### Security
- All endpoints are secured with JWT authentication.
- Role-based access control enforced on every protected endpoint.
- Instructors can only manage their own courses.
- Learners can only access progress and certificates tied to their own account.
- S3 certificate URLs should be pre-signed (time-limited) when served to the client.
- Enrolment codes are not exposed in public API responses for course listings.

### Scalability
- Kafka decouples certificate generation from the request-response cycle — the backend is not blocked during PDF generation.
- The FastAPI AI service is stateless and can be horizontally scaled independently.

### Data Integrity
- Sequential lesson/module progression is enforced at the API layer — the server validates prerequisites before recording any `LessonProgress`.
- Course locking is enforced at the API layer — any write request to a locked course's structure returns `409 Conflict` with an explanatory message.

---

## 11. Tech Stack Summary

| Layer | Technology |
|---|---|
| **Frontend** | React (web) |
| **API Gateway** | Traefik v3 |
| **Course Service** | Java 17 + Spring Boot 3 (`apps/course-service`) |
| **User Service** | Java 17 + Spring Boot 3 (`apps/user-service`) |
| **Certificate Service** | Java 17 + Spring Boot 3 (`apps/cert-service`) |
| **AI Microservice** | Python + FastAPI + LangChain (`apps/ai-service`) |
| **Databases** | MySQL 8 — one database per service |
| **Message Broker** | Apache Kafka |
| **Blob Storage** | AWS S3 |
| **Email Service** | Mailgun |
| **PDF Generation** | Thymeleaf + Flying Saucer (in Certificate Service) |
| **Vector Store** | ChromaDB (or Pinecone free tier) |
| **LLM** | Cerebras Inference API (Llama 3.3 70B) via `langchain-cerebras` |
| **Authentication** | JWT (Spring Security, issued by User Service) |
| **API Style** | RESTful JSON |

---

*End of Document*
