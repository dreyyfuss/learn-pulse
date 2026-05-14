# LearnPulse — REST API Specification
**Version:** 1.1
**Companion to:** `PRD.md`
**Base URL (dev):** `http://localhost` (all traffic enters via Traefik on port 80)
**Auth:** `Authorization: Bearer <JWT>` on every endpoint marked Auth ≠ `Public`.

---

## 0. Infrastructure & Routing

### 0.1 Traefik API Gateway

Traefik is the single public entry point for all client traffic. It terminates TLS (staging/prod), serves the React SPA as static files (via a separate static file server container), and routes API calls to the correct backend service based on path prefix. **Clients never talk directly to any backend service or FastAPI.**

```
Client (browser)
      │  :80 / :443
      ▼
┌──────────────────────────────────────────────────────────────────┐
│                         Traefik                                   │
│                                                                   │
│  /api/auth/*                     → User Service :8081             │
│  /api/users/*                    → User Service :8081             │
│  /api/admin/users/*              → User Service :8081             │
│  /api/learner/certificates       → Certificate Service :8082      │
│  /api/certificates/*             → Certificate Service :8082      │
│  /api/ai/*                       → AI Service :9000               │
│  /api/* (all other)              → Course Service :8080           │
│  / (catch-all)                   → React SPA (static file server) │
└──────────────────────────────────────────────────────────────────┘
         │               │                │               │
         ▼               ▼                ▼               ▼
  User Svc :8081   Course Svc :8080   Cert Svc :8082  AI Svc :9000
```

**Traefik routing is configured via Docker labels on each service container.** Example labels for the User Service:
```yaml
labels:
  - "traefik.http.routers.user-svc.rule=PathPrefix(`/api/auth`) || PathPrefix(`/api/users`) || PathPrefix(`/api/admin/users`)"
  - "traefik.http.services.user-svc.loadbalancer.server.port=8081"
  - "traefik.http.routers.user-svc.middlewares=auth-ratelimit"
```

**Routing rules summary (dev):**

| Pattern | Backend | Notes |
|---|---|---|
| `/api/auth/*`, `/api/users/*`, `/api/admin/users/*` | `http://user-service:8081` | Auth, profile, admin user management |
| `/api/learner/certificates`, `/api/certificates/*` | `http://cert-service:8082` | Certificate listing and download |
| `/api/ai/*` | `http://ai-service:9000` | AI chat sessions (JWT auth via ForwardAuth) |
| `/api/*` (all other) | `http://course-service:8080` | Courses, enrolments, progress |
| `/actuator/` | `http://course-service:8080` | Blocked from public internet in prod via Traefik IP whitelist middleware |
| `/` (catch-all) | Static file server (React build output) | `try_files` equivalent for SPA deep links |

**Traefik rate limiting (applied as middleware):**
```yaml
# Defined once; attached to the user-service auth router
- "traefik.http.middlewares.auth-ratelimit.ratelimit.average=10"
- "traefik.http.middlewares.auth-ratelimit.ratelimit.period=60s"
- "traefik.http.middlewares.auth-ratelimit.ratelimit.burst=5"
```
Exceeding the limit returns `429 Too Many Requests` before the request reaches the User Service.

**Config files live under `infrastructure/traefik/`** — `traefik.dev.yml` (dynamic config, no TLS) and `traefik.prod.yml` (TLS via Let's Encrypt / self-signed).

---

### 0.2 Redis

Redis is used for four distinct purposes:

| Purpose | Key pattern | TTL | Invalidated by |
|---|---|---|---|
| **Course list cache** | `cache:courses:list:<queryHash>` | 5 min | Any course published or deleted |
| **Course detail cache** | `cache:courses:<courseId>` | 5 min | Course updated, published, or locked |
| **JWT blacklist** (suspended users) | `blacklist:user:<userId>` | Remaining JWT lifetime | Admin reinstates user |

**JWT blacklist — how it works:**
When an admin suspends a user, the backend writes `SET blacklist:user:<userId> 1 EX <remainingTtl>` using the remaining lifetime of the longest-lived token that could exist for that user (max 7 days — the refresh token TTL). The JWT auth filter checks Redis on every request; a hit returns `403 ACCOUNT_SUSPENDED` immediately, without a DB query.

**Spring Boot client:** `spring-boot-starter-data-redis` with Lettuce. `@Cacheable` / `@CacheEvict` via Spring Cache abstraction.

**Dev:** Redis 7 runs in `docker-compose.dev.yml` on port `6379`. No auth in dev; `requirepass` set via env var in staging/prod.

---

## 1. Conventions

### 1.1 Response Envelope
Every JSON response uses the same envelope (PRD §9):
```json
{
  "status": "success",
  "data":   {},
  "message": "Human-readable description"
}
```
On error, `data` is `null` and a structured error object is added:
```json
{
  "status":  "error",
  "data":    null,
  "message": "Course is locked and cannot be modified.",
  "error": {
    "code":   "COURSE_LOCKED",
    "details": { "courseId": "550e8400-e29b-41d4-a716-446655440002" }
  }
}
```

### 1.2 Standard Status Codes
| Code | Meaning |
|---|---|
| `200 OK` | Successful read / update |
| `201 Created` | Resource created |
| `204 No Content` | Successful delete |
| `400 Bad Request` | Validation error |
| `401 Unauthorized` | Missing / invalid JWT |
| `403 Forbidden` | Authenticated but lacks role / ownership; suspended account |
| `404 Not Found` | Resource missing or not visible to caller |
| `409 Conflict` | Business rule violation (e.g. course locked, lesson out of order, duplicate enrolment) |
| `422 Unprocessable Entity` | Well-formed but semantically invalid (e.g. publishing an empty course) |
| `429 Too Many Requests` | Traefik rate limit exceeded (auth endpoints) |
| `500 Internal Server Error` | Unhandled server error |

### 1.3 Error Codes (canonical)
| `error.code` | When |
|---|---|
| `VALIDATION_ERROR` | Request body fails validation |
| `INVALID_CREDENTIALS` | Login email/password mismatch |
| `ACCOUNT_SUSPENDED` | User has `status = SUSPENDED` |
| `FORBIDDEN_ROLE` | JWT lacks the required role |
| `NOT_OWNER` | Instructor does not own the resource |
| `COURSE_LOCKED` | Edit attempted on a locked course |
| `COURSE_NOT_PUBLISHABLE` | Publish attempted with no modules / empty modules |
| `LESSON_OUT_OF_ORDER` | Lesson completed before its prerequisite |
| `MODULE_LOCKED` | Lesson outside the currently unlocked module |
| `ENROLMENT_CODE_INVALID` | Wrong code on a private course |
| `ALREADY_ENROLLED` | Duplicate enrolment attempt |
| `CERTIFICATE_NOT_READY` | Download requested before certificate is issued |

### 1.4 Pagination
List endpoints accept `?page=0&size=20&sort=field,asc|desc`. Responses include:
```json
{
  "data": {
    "items": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7
  }
}
```

### 1.5 Authentication
- `POST /api/auth/login` returns an access token (15 min) and a refresh token (7 days).
- JWT payload: `{ sub: userId, email, roles: ["LEARNER","INSTRUCTOR"], iat, exp }`.
- All non-public endpoints require `Authorization: Bearer <token>`.

---

## 2. Auth & Account

### POST `/api/auth/register`
**Auth:** Public
**Body:**
```json
{ "fullName": "Ada Lovelace", "email": "ada@example.com", "password": "********", "registerAsInstructor": false }
```
**Response 201:**
```json
{ "status": "success", "data": { "userId": "550e8400-e29b-41d4-a716-446655440001", "roles": ["LEARNER"] }, "message": "Account created." }
```
**Errors:** `400 VALIDATION_ERROR`, `409 EMAIL_TAKEN`.

### POST `/api/auth/login`
**Auth:** Public
**Body:** `{ "email": "...", "password": "..." }`
**Response 200:** `{ "data": { "accessToken": "...", "refreshToken": "...", "user": { "id":123, "fullName":"...", "roles":["LEARNER","INSTRUCTOR"] } } }`
**Errors:** `401 INVALID_CREDENTIALS`, `403 ACCOUNT_SUSPENDED`.

### POST `/api/auth/refresh`
**Auth:** Public (uses refresh token in body)
**Body:** `{ "refreshToken": "..." }`
**Response 200:** new access + refresh tokens.

### GET `/api/users/me`
**Auth:** Any authenticated.
**Response 200:** Profile + roles.

### PATCH `/api/users/me`
**Auth:** Any authenticated.
**Body:** `{ "fullName"?: "...", "password"?: "..." }`
**Response 200:** updated profile.

---

## 3. Admin: Users

### GET `/api/admin/users`
**Auth:** `ADMIN`. Supports pagination + `?role=INSTRUCTOR&status=ACTIVE&q=search`.

### PATCH `/api/admin/users/{id}/promote`
**Auth:** `ADMIN`. Adds `ADMIN` role to user. Idempotent (returns `200` if already admin).

### PATCH `/api/admin/users/{id}/suspend`
**Auth:** `ADMIN`. Sets `users.status = SUSPENDED`. Also writes `SET blacklist:user:<userId> 1 EX <7days>` to Redis so the JWT auth filter blocks the user immediately without a DB hit. Active sessions return `403 ACCOUNT_SUSPENDED` on the very next request.

### PATCH `/api/admin/users/{id}/reinstate`
**Auth:** `ADMIN`. Reverses suspension and deletes the Redis blacklist key.

---

## 4. Courses

### GET `/api/courses`
**Auth:** Any authenticated.
**Query:** `?q=&category=&visibility=PUBLIC|PRIVATE&page=&size=`.
**Behaviour:** Returns only `PUBLISHED` courses. `enrolment_code` is **never** included in the response (PRD §10 Security).
**Caching:** Response is cached in Redis under `cache:courses:list:<sha256(queryString)>` for **5 minutes**. Evicted on any `POST /api/courses/{id}/publish` or `DELETE /api/courses/{id}`.

### GET `/api/courses/{id}`
**Auth:** Any authenticated.
**Response:** Course + module/lesson outline (titles + order only — content URLs are gated by enrolment).
**Caching:** Cached under `cache:courses:<courseId>` for **5 minutes**. Evicted on course update, publish, lock, or delete.

### POST `/api/courses`
**Auth:** `INSTRUCTOR`.
**Body:** `{ "title", "description", "thumbnailUrl?", "category?", "visibility": "PUBLIC|PRIVATE" }`
**Response 201:** `{ "courseId": "550e8400-e29b-41d4-a716-446655440002", "enrolmentCode": "AB12CD34" }` (code only present for `PRIVATE`).

### PATCH `/api/courses/{id}`
**Auth:** `INSTRUCTOR` (owner).
**Body:** any subset of editable fields.
**Errors:** `409 COURSE_LOCKED` if the course has been started by any learner.

### POST `/api/courses/{id}/publish`
**Auth:** `INSTRUCTOR` (owner).
**Behaviour:** Validates ≥ 1 module and each module has ≥ 1 lesson. Sets `status = PUBLISHED`, `published_at`, and produces a `course.published` Kafka event.
**Errors:** `422 COURSE_NOT_PUBLISHABLE`.

### DELETE `/api/courses/{id}`
**Auth:** `ADMIN`. Cascades to modules, lessons, enrolments, progress, certificates.

### GET `/api/courses/{id}/enrolment-code`
**Auth:** `INSTRUCTOR` (owner).
**Response 200:** `{ "enrolmentCode": "AB12CD34" }` — the private enrolment code for sharing with learners.

### GET `/api/instructor/courses`
**Auth:** `INSTRUCTOR`. Returns courses owned by the caller including `DRAFT`.

---

## 5. Modules & Lessons

> All write endpoints in this section return `409 COURSE_LOCKED` if the course has any started enrolment.

### POST `/api/courses/{courseId}/modules`
**Auth:** `INSTRUCTOR` (owner).
**Body:** `{ "title", "description", "orderIndex" }`

### PUT `/api/courses/{courseId}/modules/reorder`
**Auth:** `INSTRUCTOR` (owner).
**Body:** `{ "moduleIds": ["<uuid1>", "<uuid2>", ...] }` — ordered list of all module UUIDs.
**Response 204**

### PATCH `/api/courses/{courseId}/modules/{id}`
**Auth:** `INSTRUCTOR` (owner). Reorder via `orderIndex`.

### DELETE `/api/courses/{courseId}/modules/{id}`
**Auth:** `INSTRUCTOR` (owner).

### POST `/api/courses/{courseId}/modules/{moduleId}/lessons`
**Auth:** `INSTRUCTOR` (owner).
**Body:** `{ "title", "description", "contentType": "VIDEO|DOCUMENT|ARTICLE|OTHER", "contentUrl", "orderIndex", "attachments?": [ { "fileName", "s3Url", "mimeType" } ] }`

### PUT `/api/courses/{courseId}/modules/{moduleId}/lessons/reorder`
**Auth:** `INSTRUCTOR` (owner).
**Body:** `{ "lessonIds": ["<uuid1>", "<uuid2>", ...] }` — ordered list of all lesson UUIDs in the module.
**Response 204**

### PATCH `/api/courses/{courseId}/modules/{moduleId}/lessons/{id}`
**Auth:** `INSTRUCTOR` (owner).

### DELETE `/api/courses/{courseId}/modules/{moduleId}/lessons/{id}`
**Auth:** `INSTRUCTOR` (owner).

---

## 6. Enrolments

### POST `/api/enrolments`
**Auth:** `LEARNER`.
**Body (public course):** `{ "courseId": "550e8400-e29b-41d4-a716-446655440002" }`
**Body (private course):** `{ "courseId": "550e8400-e29b-41d4-a716-446655440002", "enrolmentCode": "AB12CD34" }`
**Response 201:** `{ "enrolmentId": "550e8400-e29b-41d4-a716-446655440003", "status": "ACTIVE", "startedAt": null }`
**Side effect:** Produces a `user.enrolled` Kafka event.
**Errors:** `409 ALREADY_ENROLLED`, `403 ENROLMENT_CODE_INVALID`.

### POST `/api/enrolments/{id}/start`
**Auth:** `LEARNER` (owner).
**Behaviour:** Sets `enrolments.started_at = NOW()`, sets `courses.is_locked = 1` if not already, unlocks Module 1 (`module_unlocks` row). Idempotent — repeated calls return `200` with the existing `startedAt`.
**Response 200:** `{ "startedAt": "2026-05-01T13:00:00Z", "unlockedModuleId": 11 }`.

### GET `/api/learner/enrolments`
**Auth:** `LEARNER`. Returns the caller's enrolments with progress summary.

### POST `/api/admin/enrolments`
**Auth:** `ADMIN`. Body: `{ "userId", "courseId" }`. Same side effects as a learner-initiated enrolment.

### DELETE `/api/admin/enrolments/{id}`
**Auth:** `ADMIN`. Hard delete; cascades `lesson_progress` and `module_unlocks` for that enrolment but leaves `certificates` intact.

---

## 7. Progress

### POST `/api/lessons/{lessonId}/complete`
**Auth:** `LEARNER` (must have an active enrolment in the lesson's course).
**Behaviour:**
1. Validate enrolment exists and `started_at` is set.
2. Validate the lesson's module is currently unlocked for this enrolment.
3. Validate all lessons with a lower `order_index` in the same module are already `COMPLETED`.
4. Insert `lesson_progress` row.
5. If this was the last lesson in the module:
   - If a next module exists → unlock it and produce `module.unlocked`.
   - Else → set `enrolments.status = COMPLETED`, `completed_at = NOW()`, produce `course.completed`.
**Response 200:** `{ "lessonId": "550e8400-e29b-41d4-a716-446655440009", "completedAt": "...", "moduleUnlocked?": { "moduleId": "550e8400-e29b-41d4-a716-44665544000c" }, "courseCompleted?": true }`
**Errors:** `409 LESSON_OUT_OF_ORDER`, `409 MODULE_LOCKED`.

### GET `/api/enrolments/{id}/progress`
**Auth:** `LEARNER` (owner) or `INSTRUCTOR` (course owner) or `ADMIN`.
**Response:** Module/lesson tree annotated with `completed`, `unlocked`, `currentLessonId`.

---

## 8. Certificates

### GET `/api/learner/certificates`
**Auth:** `LEARNER`.
**Response:** array of `{ certificateId, courseId, courseName, issuedAt }`.

### GET `/api/certificates/{id}/download`
**Auth:** `LEARNER` (owner).
**Behaviour:** `302 Found` redirect to a pre-signed S3 URL valid for 5 minutes.
**Errors:** `404` if not owned, `409 CERTIFICATE_NOT_READY` if the row exists in `enrolments` (`COMPLETED`) but the certificate row hasn't yet been written (Kafka consumer in flight).

---

## 9. Analytics

> **Status: Planned — not yet implemented.**

### GET `/api/instructor/courses/{id}/analytics`
**Auth:** `INSTRUCTOR` (owner).
**Response:**
```json
{
  "aggregate": { "enrolments": 120, "completions": 47, "completionRate": 39.2, "active": 73 },
  "learners": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440001", "fullName": "...", "email": "...",
      "status": "IN_PROGRESS",
      "currentLesson": { "lessonId": "550e8400-e29b-41d4-a716-446655440009", "title": "..." },
      "enrolledAt": "...", "startedAt": "...", "completedAt": null
    }
  ]
}
```

### GET `/api/admin/analytics`
**Auth:** `ADMIN`.
**Response:**
```json
{
  "users":   { "total": 540, "byRole": { "LEARNER": 500, "INSTRUCTOR": 35, "ADMIN": 5 } },
  "courses": { "total": 60, "byStatus": { "DRAFT": 10, "PUBLISHED": 50 } },
  "enrolments": { "total": 4200, "completed": 1100, "completionRate": 26.2 }
}
```

---

## 10. AI Service (FastAPI) — Public Contract

> Routed through Traefik at `/api/ai/*` with the same `jwt-auth` ForwardAuth middleware as all protected endpoints. Traefik injects `X-User-Id`, `X-User-Email`, and `X-User-Roles` headers. The AI service reads `X-User-Id` to identify the caller and calls the Course Service internal endpoint to verify enrolment before creating a session.

### POST `/api/ai/courses/{courseId}/sessions`
**Auth:** Any authenticated learner (JWT via Traefik).
**Behaviour:** Verifies the caller is enrolled in the course, then creates a stateful chat session backed by ChromaDB context for that course.
**Response 201:**
```json
{ "sessionId": "550e8400-e29b-41d4-a716-446655440099" }
```
**Errors:** `403` if not enrolled or course not found.

### POST `/api/ai/courses/{courseId}/sessions/{sessionId}/messages`
**Auth:** Any authenticated learner (JWT via Traefik, must be session owner).
**Body:** `{ "message": "What is covered in Module 2?" }`
**Response 200:** Server-Sent Events stream.
```
data: {"token": "Module"}
data: {"token": " 2"}
data: {"token": " covers..."}
data: [DONE]
```
On error, a final SSE frame is emitted before `[DONE]`:
```
data: {"error": "The AI is busy right now — please try again in a moment."}
```
**Errors (streamed):** rate-limit (Cerebras 30 RPM), session not found, forbidden.

### GET `/api/ai/stream-test`
**Auth:** Public (no JWT required). Diagnostic endpoint — streams 10 mock tokens, one per second. Used to verify SSE plumbing end-to-end.

---

## 11. Content Upload & Retrieval

All endpoints share the base path `/api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}`.

### POST `.../content/upload-url`
Returns a 15-minute presigned PUT URL for direct browser-to-S3 upload.

**Auth:** `INSTRUCTOR` (must own the course)  
**Request:** `{ "mimeType": "video/mp4" }`  
**Response 200:** `{ "data": { "uploadUrl": "http://...", "objectKey": "lessons/{id}/content.mp4" } }`

---

### POST `.../content/confirm`
Persists the S3 object key on the lesson record after the browser PUT succeeds.

**Auth:** `INSTRUCTOR` (must own the course)  
**Request:** `{ "objectKey": "lessons/{id}/content.mp4" }`  
**Response 204**

---

### GET `.../content`
Returns a 1-hour presigned GET URL. Falls back to `fallbackUrl` (`content_url`) when no S3 key exists.

**Auth:** Instructor (owns course) OR enrolled learner with module unlocked.  
**Response 200:** `{ "data": { "contentType": "VIDEO", "presignedUrl": "...", "fallbackUrl": null } }`  
**Errors:** `403` module not yet unlocked for learner.

---

### DELETE `.../content`
Removes the S3 object and clears `content_key`.

**Auth:** `INSTRUCTOR` (must own the course)  
**Response 204**

---

### POST `.../attachments/upload-url`
Returns a 15-minute presigned PUT URL for a supplementary lesson attachment.

**Auth:** `INSTRUCTOR` (must own the course)  
**Request:** `{ "fileName": "notes.pdf", "mimeType": "application/pdf" }`  
**Response 200:** `{ "data": { "uploadUrl": "...", "objectKey": "lessons/{id}/attachments/notes.pdf" } }`

---

### POST `.../attachments/confirm`
Creates a `LessonAttachment` record after upload.

**Auth:** `INSTRUCTOR` (must own the course)  
**Request:** `{ "objectKey": "lessons/{id}/attachments/notes.pdf", "fileName": "notes.pdf", "mimeType": "application/pdf" }`  
**Response 201:** `AttachmentResponse { id, fileName, s3Key, mimeType }`

---

### GET `.../attachments/{attachmentId}/download-url`
Returns a 1-hour presigned GET URL for an attachment. Returns legacy `s3_url` directly if no S3 key is set.

**Auth:** Instructor (owns course) OR enrolled learner with module unlocked.  
**Response 200:** `{ "data": { "url": "http://..." } }`

---

## 12. Health & Ops

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Spring Boot liveness/readiness |
| `GET /actuator/info` | Build info |
| `GET /healthz` | FastAPI liveness |

---

*End of Document*
