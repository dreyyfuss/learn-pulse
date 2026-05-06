# LearnPulse — REST API Specification
**Version:** 1.1
**Companion to:** `PRD.md`
**Base URL (dev):** `http://localhost` (all traffic enters via Nginx on port 80)
**Auth:** `Authorization: Bearer <JWT>` on every endpoint marked Auth ≠ `Public`.

---

## 0. Infrastructure & Routing

### 0.1 Nginx Reverse Proxy

Nginx is the single public entry point for all client traffic. It terminates TLS (staging/prod), serves the React SPA as static files, and proxies API calls to the appropriate backend service. **Clients never talk directly to Spring Boot or FastAPI.**

```
Client (browser)
      │  :80 / :443
      ▼
┌─────────────────────────────────────────────────────┐
│                    Nginx                             │
│                                                     │
│  location /api/          → proxy_pass :8080         │
│  location /actuator/     → proxy_pass :8080  (internal only) │
│  location /              → serve React SPA (try_files) │
└─────────────────────────────────────────────────────┘
         │                        │
         ▼                        ▼
  Spring Boot :8080         React static files
         │
  (internally calls)
         │
         ▼
  FastAPI :9000  (NOT exposed through Nginx — internal only)
```

> The FastAPI AI service is **not** routed through Nginx. Spring Boot acts as the proxy (`POST /api/courses/{id}/ai/chat` → `POST /ai/courses/{id}/chat` on the internal network). This keeps the AI service entirely behind the backend security layer.

**`infrastructure/nginx/nginx.conf` routing rules (dev):**

| Pattern | Backend | Notes |
|---|---|---|
| `/api/` | `http://api:8080` | All REST endpoints |
| `/actuator/` | `http://api:8080` | Blocked from public internet in prod via `allow 10.0.0.0/8; deny all;` |
| `/` (catch-all) | React build (`/usr/share/nginx/html`) | `try_files $uri $uri/ /index.html` for SPA deep links |

**Nginx rate limiting (applied at the proxy layer):**
```nginx
limit_req_zone $binary_remote_addr zone=auth:10m rate=10r/m;

location /api/auth/login    { limit_req zone=auth burst=5 nodelay; proxy_pass ...; }
location /api/auth/register { limit_req zone=auth burst=5 nodelay; proxy_pass ...; }
```
Exceeding the limit returns `429 Too Many Requests` before the request reaches Spring Boot.

---

### 0.2 Redis

Redis is used for four distinct purposes:

| Purpose | Key pattern | TTL | Invalidated by |
|---|---|---|---|
| **Course list cache** | `cache:courses:list:<queryHash>` | 5 min | Any course published or deleted |
| **Course detail cache** | `cache:courses:<courseId>` | 5 min | Course updated, published, or locked |
| **Analytics cache** | `cache:analytics:instructor:<courseId>` / `cache:analytics:admin` | 60 s | Any enrolment or completion event |
| **JWT blacklist** (suspended users) | `blacklist:user:<userId>` | Remaining JWT lifetime | Admin reinstates user |
| **AI reply cache** | `ai:cache:<courseId>:<messageHash>` | 1 hour | Never (course content is immutable once locked) |

**JWT blacklist — how it works:**
When an admin suspends a user, the backend writes `SET blacklist:user:<userId> 1 EX <remainingTtl>` using the remaining lifetime of the longest-lived token that could exist for that user (max 7 days — the refresh token TTL). The JWT auth filter checks Redis on every request; a hit returns `403 ACCOUNT_SUSPENDED` immediately, without a DB query.

**AI reply cache — rationale:**
Cerebras free-tier has a 30 RPM limit (PRD §7.2). Caching identical messages per course shields against repeated questions during a demo or a busy class. Cache key is `sha256(courseId + normalise(message))`. On a cache hit the FastAPI service is not called at all.

**Spring Boot client:** `spring-boot-starter-data-redis` with Lettuce. `@Cacheable` / `@CacheEvict` via Spring Cache abstraction; the AI reply cache is managed manually with `RedisTemplate`.

**FastAPI client:** `redis.asyncio` (async Redis client). Used only for the AI reply cache lookup/write.

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
    "details": { "courseId": 456 }
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
| `429 Too Many Requests` | Nginx rate limit exceeded (auth endpoints) |
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
{ "status": "success", "data": { "userId": 123, "roles": ["LEARNER"] }, "message": "Account created." }
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
**Response 201:** `{ "courseId": 456, "enrolmentCode": "AB12CD34" }` (code only present for `PRIVATE`).

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

### GET `/api/instructor/courses`
**Auth:** `INSTRUCTOR`. Returns courses owned by the caller including `DRAFT`.

---

## 5. Modules & Lessons

> All write endpoints in this section return `409 COURSE_LOCKED` if the course has any started enrolment.

### POST `/api/courses/{courseId}/modules`
**Auth:** `INSTRUCTOR` (owner).
**Body:** `{ "title", "description", "orderIndex" }`

### PATCH `/api/courses/{courseId}/modules/{id}`
**Auth:** `INSTRUCTOR` (owner). Reorder via `orderIndex`.

### DELETE `/api/courses/{courseId}/modules/{id}`
**Auth:** `INSTRUCTOR` (owner).

### POST `/api/courses/{courseId}/modules/{moduleId}/lessons`
**Auth:** `INSTRUCTOR` (owner).
**Body:** `{ "title", "description", "contentType": "VIDEO|DOCUMENT|ARTICLE|OTHER", "contentUrl", "orderIndex", "attachments?": [ { "fileName", "s3Url", "mimeType" } ] }`

### PATCH `/api/courses/{courseId}/modules/{moduleId}/lessons/{id}`
**Auth:** `INSTRUCTOR` (owner).

### DELETE `/api/courses/{courseId}/modules/{moduleId}/lessons/{id}`
**Auth:** `INSTRUCTOR` (owner).

---

## 6. Enrolments

### POST `/api/enrolments`
**Auth:** `LEARNER`.
**Body (public course):** `{ "courseId": 456 }`
**Body (private course):** `{ "courseId": 456, "enrolmentCode": "AB12CD34" }`
**Response 201:** `{ "enrolmentId": 789, "status": "ACTIVE", "startedAt": null }`
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
**Response 200:** `{ "lessonId": 1, "completedAt": "...", "moduleUnlocked?": { "moduleId": 12 }, "courseCompleted?": true }`
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

### GET `/api/instructor/courses/{id}/analytics`
**Auth:** `INSTRUCTOR` (owner).
**Caching:** Cached under `cache:analytics:instructor:<courseId>` for **60 seconds**. Evicted on any enrolment or lesson-completion event for this course.
**Response:**
```json
{
  "aggregate": { "enrolments": 120, "completions": 47, "completionRate": 39.2, "active": 73 },
  "learners": [
    {
      "userId": 1, "fullName": "...", "email": "...",
      "status": "IN_PROGRESS",
      "currentLesson": { "lessonId": 9, "title": "..." },
      "enrolledAt": "...", "startedAt": "...", "completedAt": null
    }
  ]
}
```

### GET `/api/admin/analytics`
**Auth:** `ADMIN`.
**Caching:** Cached under `cache:analytics:admin` for **60 seconds**. Evicted on any enrolment or completion event platform-wide.
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

> Mounted under a separate origin (e.g. `http://localhost:9000`). Spring Boot calls FastAPI on the learner's behalf after validating enrolment.

### POST `/ai/courses/{courseId}/chat`
**Auth:** Service-to-service shared secret in `X-Service-Auth`. The Spring Boot proxy is responsible for verifying the learner's enrolment.
**Body:**
```json
{
  "userId": 123,
  "message": "What is covered in Module 2?",
  "chatHistory": [ { "role": "user|assistant", "content": "..." } ]
}
```
**Response 200:**
```json
{
  "reply": "Module 2 covers...",
  "sourceLessons": [ { "lessonId": 9, "title": "..." } ]
}
```
**Errors:** `404 COURSE_NOT_INDEXED` if the `course.published` event has not yet been processed.

### Spring Boot proxy
For UI simplicity the frontend calls Spring Boot, not FastAPI directly:

`POST /api/courses/{courseId}/ai/chat` → enforces enrolment, then forwards to FastAPI.

---

## 11. Health & Ops

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Spring Boot liveness/readiness |
| `GET /actuator/info` | Build info |
| `GET /healthz` | FastAPI liveness |

---

*End of Document*
