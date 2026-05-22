# LearnPulse — Product Requirements Document
**Version:** 2.0  
**Programme:** Moniepoint DreamDev Capstone  
**Status:** Final  
**Last Updated:** May 2026

---

## Table of Contents
1. [Overview](#1-overview)
2. [The Problem](#2-the-problem)
3. [Goals & Non-Goals](#3-goals--non-goals)
4. [User Personas](#4-user-personas)
5. [Actors & Roles](#5-actors--roles)
6. [Feature Specifications](#6-feature-specifications)
7. [Non-Functional Requirements](#7-non-functional-requirements)
8. [Supporting Documents](#8-supporting-documents)

---

## 1. Overview

**LearnPulse** is an online learning platform built around a simple idea: most platforms tell you who signed up. LearnPulse tells you who learned.

The platform serves two users simultaneously. **Instructors** (or team leads, managers, subject-matter experts) who need to turn their knowledge into a structured course — fast — and **learners** who need to stay on track, engage with content, and prove they understood it.

> *Intelligent learning, engineered for scale.*

---

## 2. The Problem

Only 1 in 7 learners finish what they start (Coursera / Udemy industry data). LearnPulse is built to close that gap by tackling three root causes:

| # | Problem | How LearnPulse solves it |
|---|---|---|
| 01 | **Nobody finishes.** Learners enrol, disappear, and nobody notices. | Sequential progression, daily streaks, and email nudges keep learners moving forward. |
| 02 | **Creating a course takes too long.** Experts aren't instructional designers. Knowledge stays locked in someone's head. | The AI Course Builder turns a plain-text description into a complete course — modules, articles, and quizzes — in minutes. |
| 03 | **There is no proof learning happened.** The platform records who clicked Start. Not who understood. | Quizzes test comprehension at every step. Verified PDF certificates prove completion. |

---

## 3. Goals & Non-Goals

### Goals
- Let instructors create and publish structured courses (Course → Module → Lesson) manually or via the AI Course Builder.
- Let learners enrol in public courses or access private courses via an enrolment code.
- Enforce sequential lesson and module progression to prevent skipping ahead.
- Test comprehension with per-module quizzes (MCQ and True/False).
- Generate and deliver a verified PDF certificate exactly once per completed course.
- Track daily learning streaks to encourage consistent engagement.
- Provide an AI Study Assistant grounded solely in the course's own content — no hallucination from outside sources.
- Provide instructors with per-learner and aggregate completion analytics.
- Give admins platform-wide visibility and moderation tools.
- Allow instructors to upload lesson content (video, document, Markdown article) and serve it securely to enrolled learners.

### Non-Goals
- Payments and billing.
- Native mobile applications (web only).
- Editing a course after a learner has started it.
- Sub-module hierarchies or lessons that exist outside a module.
- Leaderboards or social/community features (future roadmap).

---

## 4. User Personas

**Yinka — Engineering Manager**  
Six new hires starting next month. The onboarding playbook lives in her head. She needs to turn that expertise into a structured course quickly, share it privately with her team, and know which new hires actually understood it — not just who clicked through.

**Tobi — Solo Learner**  
Eager to grow. Starts every course. Finishes almost none. Learns better when he can ask questions and when the platform makes it feel like progress is happening. Needs nudges, interaction, and something to show for his effort.

---

## 5. Actors & Roles

| Role | Description |
|---|---|
| **Learner** | Enrols in courses, progresses through lessons, takes quizzes, earns certificates. |
| **Instructor** | Creates and manages courses; views enrolment and completion analytics. |
| **Admin** | Manages platform users and content; views platform-wide analytics. |

**Role rules:**
- A single account can hold multiple roles simultaneously (e.g., an engineering manager can be both Instructor and Learner).
- Instructors self-register with the Instructor role immediately — no admin approval.
- The first Admin account is seeded on first boot. Subsequent admins are promoted by an existing admin.
- Users who hold both Instructor and Learner roles see a **mode switcher** in the navigation bar to move between `/teach/...` and `/learn/...` route namespaces.

---

## 6. Feature Specifications

### 6.1 Authentication & User Management

- Learners and instructors self-register with email and password. Instructors select "Register as Instructor" at sign-up.
- JWT-based authentication. All tokens are validated centrally at the API gateway — individual services never handle raw tokens.
- Admins can promote any user to Admin, and suspend or reinstate any account. Suspended users cannot log in.

---

### 6.2 Course Authoring (Manual)

Instructors create courses with a title, description, category/tags, thumbnail, and visibility setting (Public or Private). Private courses generate a shareable enrolment code.

**Course lifecycle:**
- `DRAFT` — only visible to the instructor; freely editable.
- `PUBLISHED` — visible to learners; still editable until the first learner starts it.
- `LOCKED` — the moment any learner clicks "Start Course," the course is locked. Instructors see a read-only view and cannot change structure or content. This protects the progress records of all active learners.

A course must have at least one module, and each module at least one lesson, before it can be published.

---

### 6.3 AI Course Builder

> *From expert to instructor in minutes. Describe it — we'll structure it.*

An instructor writes a plain-text description of the course they want to create. The AI Course Builder generates a complete course asynchronously:

- **Structure:** 3–5 modules, each with 3–5 lessons.
- **Content:** Each lesson gets a full Markdown article.
- **Assessment:** Each lesson gets a ready-to-use quiz (3–5 questions).

The result is a `DRAFT` course in the instructor's dashboard, ready to review, edit, and publish through the normal flow. The instructor retains full control — AI generation is a starting point, not a final product.

Generation status can be polled until it reaches `COMPLETED` or `FAILED`.

---

### 6.4 Content Upload & Delivery

Instructors upload lesson content directly to the platform. Supported content types:

| Type | Format | Learner view |
|---|---|---|
| Video | MP4 / WebM | HTML5 video player |
| Document | PDF, DOCX, ZIP, etc. | Inline viewer + download button |
| Article | Markdown (`.md`) | Rendered article |

Files are stored in object storage. Learners access content via time-limited secure URLs — direct storage access is never exposed. Content generated by the AI Course Builder (Markdown articles) is uploaded automatically using the same storage path.

Upload access requires course ownership. Retrieval access requires an active enrolment with the relevant module unlocked.

---

### 6.5 Enrolment & Progression

**Enrolling:**
- Public courses: one-click enrolment.
- Private courses: learner enters the enrolment code shown on the course page.

**Starting:**
- Enrolment alone does not unlock content. The learner must explicitly click **"Start Course"**, which locks the course and unlocks Module 1.

**Progression rules:**
- Lessons must be completed in order within a module.
- Completing the last lesson of a module automatically unlocks the next module.
- Completing the last lesson of the last module triggers course completion.
- Lesson completion is irreversible.

**Unenrolment:** Admins can manually unenrol a learner. Learners cannot self-unenrol.

---

### 6.6 Quizzes

Instructors can attach quizzes to modules. Each quiz contains ordered questions — Multiple Choice (4 options, one correct) or True/False (2 options) — and has a configurable passing score.

**Learner flow:**
1. The learner completes all lessons in a module.
2. The learner opens the module's quiz and answers the questions.
3. The system scores the attempt and records whether the learner passed.
4. The learner may retake the quiz. Their best attempt is always retrievable.

Quizzes respect the same course-locking rules as lessons — they cannot be edited once a learner has started the course.

---

### 6.7 Learning Streaks

A learner's streak counts how many consecutive calendar days they have completed at least one lesson. The streak is displayed on the learner dashboard.

**Rules:**
- Completing a lesson on a new day increments the streak.
- Missing a day resets the current streak to 1 on the next active day.
- Multiple completions on the same day count as one streak day.
- The platform also tracks each learner's longest streak ever.

---

### 6.8 AI Study Assistant

Enrolled learners can open a chat panel in the course player and ask questions about the course. The assistant answers only from that course's content — it is grounded in the actual lessons and cannot respond with information from outside the course.

Access requires an active enrolment and the course must have been started. The assistant is available on a per-course basis; each course has its own isolated knowledge base.

---

### 6.9 Certificates

On completing a course, the platform generates a verified PDF certificate and emails it to the learner. The certificate includes the learner's name, course name, instructor name, completion date, and a unique certificate ID.

Certificates are stored in object storage. Learners can download them at any time from their dashboard via a time-limited secure link.

**Exactly-once guarantee:** The system ensures a certificate is issued exactly once per learner per course, even under delivery failures or retries. See [`docs/kafka-events.md`](kafka-events.md) for the technical guarantee.

---

### 6.10 Analytics

**Instructor analytics (per course):**
- Total enrolments, completions, and active learners.
- Completion rate (%).
- Per-learner breakdown: status, current lesson, dates enrolled / started / completed.

**Admin analytics (platform-wide):**
- Total users by role, total courses by status.
- Platform-wide enrolment and completion counts and rates.

---

### 6.11 Admin Dashboard

- View, suspend, reinstate, and promote any user account.
- View and delete any course on the platform.
- Manually enrol or unenrol any learner from any course.
- View platform analytics.

---

## 7. Non-Functional Requirements

| Dimension | Requirement |
|---|---|
| **Performance** | Standard CRUD responses < 300ms (p95). Certificate generation < 30s end-to-end. AI chat responses < 5s (p95). |
| **Reliability** | Certificate delivery is guaranteed exactly once via a two-layer idempotency mechanism. Async events are never lost — written transactionally before being dispatched. |
| **Security** | All endpoints require JWT authentication. Role-based access control is enforced on every API call. Instructors can only modify their own courses. Learners can only access their own progress and certificates. All file URLs are time-limited presigned links. Enrolment codes are never exposed in public course listings. Rate limiting is applied to auth endpoints. |
| **Scalability** | Certificate generation and AI course generation are fully decoupled from the request cycle via Kafka — the API responds immediately and work completes asynchronously. |
| **Data integrity** | Sequential progression is enforced server-side. Course locking is enforced server-side. A completed lesson cannot be un-completed. |

---

## 8. Supporting Documents

| Document | Contents |
|---|---|
| [`docs/api-spec.md`](api-spec.md) | Full REST API reference — endpoints, request/response shapes |
| [`docs/kafka-events.md`](kafka-events.md) | Kafka topics, event payloads, consumer contracts |
| [`docs/ERD.md`](ERD.md) | Entity-relationship diagram |
| [`docs/sequence-diagrams.md`](sequence-diagrams.md) | Sequence diagrams for key flows |
| [`docs/plan.md`](plan.md) | Implementation plan and architectural decisions |
| [`docs/security.md`](security.md) | Security review |
| [`docs/perf.md`](perf.md) | Performance benchmarks |

---

*End of Document*
