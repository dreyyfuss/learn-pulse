# LearnPulse — Security Review

---

## Authentication and Authorization

JWT access tokens (15-minute expiry, HS256-signed) are issued exclusively by the User Service. Refresh tokens carry a 7-day expiry and are held client-side; the server maintains a Redis blacklist that is checked on every token validation.

All JWT validation is handled centrally by Traefik's ForwardAuth middleware, which calls `GET /api/auth/validate` before forwarding any protected request. Downstream services (Course Service, Certificate Service, AI Service) never see or parse raw tokens -- they read pre-validated `X-User-Id`, `X-User-Email`, and `X-User-Roles` headers injected by the gateway. This keeps auth logic in one place and removes the risk of inconsistent validation across services.

Role-based access control (LEARNER, INSTRUCTOR, ADMIN) is enforced at the method level via Spring Security `@PreAuthorize` annotations on every protected endpoint. The frontend mirrors this with a `RoleGuard` component that prevents rendering of protected routes without the required role.

Suspending a user via the admin panel writes a Redis blacklist key immediately, blocking all subsequent requests within the current token's lifetime without waiting for natural expiry.

---

## Service-to-Service Authentication

Internal calls from the AI Service to the Course Service are authenticated with a shared secret via the `X-Service-Auth` header. The secret is injected at runtime via the `SERVICE_AUTH_SECRET` environment variable. Requests with an incorrect or missing secret return `401 Unauthorized`.

---

## Secrets Management

All credentials and keys are injected via environment variables. No secrets are present in source code. `.env` files are listed in `.gitignore`; `.env.example` files contain only placeholder values. In production, `SERVICE_AUTH_SECRET`, `GROQ_API_KEY`, `MAILGUN_API_KEY`, and object storage credentials are injected via GitHub Actions repository secrets at deploy time and never written to disk.

---

## Input Validation

All request bodies are validated with Bean Validation (`@Valid`, `@NotBlank`, `@Size`, etc.) at controller boundaries before any business logic executes. SQL injection is prevented throughout by Spring Data JPA's parameterised queries -- no raw JDBC string concatenation exists in any service. XSS is mitigated on the frontend by React JSX's default escaping of all interpolated values; `dangerouslySetInnerHTML` is not used anywhere in the application.

---

## File Security

### Content Upload

Instructors upload lesson content directly to object storage via presigned PUT URLs with a 15-minute TTL. File bytes never pass through the Course Service heap. Upload access requires verified instructor ownership of the course. On confirmation, the submitted object key is validated to start with `"lessons/{lessonId}/"` -- requests with a key outside this prefix are rejected with `400 Bad Request`, preventing path traversal. Attachment filenames are sanitised to `[a-zA-Z0-9._-]` before being used as the object key suffix.

### Content Retrieval

Learner access to lesson content is served via presigned GET URLs with a 1-hour TTL, gated by an active enrolment with the relevant module unlocked. Instructors can always retrieve content for their own courses.

### Certificate Download

Before generating a presigned download URL, the Certificate Service verifies that the requesting user's ID matches the certificate owner. Presigned S3 URLs have a 5-minute TTL. Certificate bytes are never loaded into the service heap -- a `302 Found` redirect sends the browser directly to object storage.

---

## Enrolment Code Security

Enrolment codes are randomly generated 8-character uppercase alphanumeric strings stored in plain text. They are never included in public course listing or course detail responses. An invalid code lookup returns `400 ENROLMENT_CODE_INVALID` without revealing whether the code exists or which course it belongs to.

---

## Rate Limiting

Traefik's rate-limit middleware is applied to auth and sensitive endpoints at 10 requests per minute per IP with a burst allowance of 5. The AI chat endpoint is additionally protected by a Redis reply cache (keyed on `sha256(courseId + normalisedMessage)`, TTL 1 hour) that absorbs repeated questions and reduces exposure to Groq's upstream rate limits.

---

## Kafka Consumer Idempotency

All Kafka consumers write to an `idempotency_log` table in the same database transaction as their domain action. A unique constraint on `idempotency_log.event_id` prevents duplicate processing on message redelivery. For certificate issuance, a composite unique key on `(user_id, course_id)` in the `certificates` table provides a second layer of protection, ensuring exactly one certificate is ever issued per learner per course regardless of redelivery or consumer restarts.

---

## CORS

Spring Boot services are configured to accept requests from the frontend origin only. Wildcard origins (`*`) are not permitted.

---

*Last updated: May 2026*
