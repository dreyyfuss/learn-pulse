# LearnPulse — Security Checklist
**Companion to:** `plan.md §13`

---

## Authentication & Authorisation

- [x] JWT access tokens (15-minute expiry) issued at login; `HS256` signed (symmetric HMAC-SHA256 using `JWT_SECRET`)
- [x] Refresh tokens (7-day expiry) stored client-side; server-side blacklist in Redis on logout
- [x] Traefik ForwardAuth validates JWT on every request before forwarding to downstream services
- [x] Downstream services read `X-User-Id`, `X-User-Email`, `X-User-Roles` headers set by the gateway — never trust client-supplied headers
- [x] Role-based access control (`LEARNER`, `INSTRUCTOR`, `ADMIN`) enforced with Spring Security `@PreAuthorize`
- [x] `RoleRoute` component on the frontend prevents rendering of protected pages without the required role

## Service-to-Service Auth

- [x] AI service → Course Service internal calls authenticated with `X-Service-Auth: <shared-secret>`
- [x] `SERVICE_AUTH_SECRET` env var required in production; docker-compose sets a non-default value; `settings.py` default (`"change-me"`) only applies if the env var is absent
- [x] Wrong secret returns `401 Unauthorized` (plan §6.7 acceptance check passes)

## Secrets Management

- [x] All secrets passed via environment variables; no credentials in source code
- [x] `.env` files listed in `.gitignore`; `.env.example` files contain only placeholder values
- [x] `SERVICE_AUTH_SECRET`, `GROQ_API_KEY`, `MAILGUN_API_KEY` injected at deploy time via GitHub Actions secrets
- [x] S3/MinIO credentials (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) injected via environment; never hardcoded

## Input Validation

- [x] All request bodies validated with Bean Validation (`@Valid`, `@NotBlank`, `@Size`, etc.) at controller boundaries
- [x] SQL injection: Spring Data JPA parameterised queries throughout — no raw JDBC string concatenation
- [x] XSS: React JSX escapes all interpolated values by default; no `dangerouslySetInnerHTML` used

## Certificate Download

- [x] Certificate ownership verified in `CertificateController.download()` — `cert.getUserId().equals(userId)` before presigning
- [x] Presigned S3 URLs have a 5-minute TTL (`Duration.ofMinutes(5)`)
- [x] `302 Found` redirect — certificate bytes never pass through the cert-service heap

## Enrolment Code Security

- [x] Enrolment codes are generated as random 8-character uppercase alphanumeric strings and stored in plain text in `courses.enrolment_code`; they are never included in public course listing responses
- [x] Invalid code returns `400 ENROLMENT_CODE_INVALID` — does not reveal whether the code itself is valid or which course it belongs to

## Rate Limiting

- [x] Traefik rate-limit middleware: 10 requests/minute per IP, burst 5 (`traefik.dev.yml`)
- [x] AI chat endpoint additionally guarded by Groq free-tier limits; Redis reply cache (key `aicache:<sha256>`, TTL 1 h) absorbs repeated questions and reduces LLM API calls

## Kafka Consumer Idempotency

- [x] All Kafka consumers write to `idempotency_log` in the same DB transaction as their domain action
- [x] Unique constraint on `idempotency_log.event_id` prevents duplicate processing on redelivery
- [x] `certificates` table has composite unique key `(user_id, course_id)` as a second layer of protection

## Content Upload & Retrieval

- [x] Presigned PUT URLs for lesson content and attachment uploads have a 15-minute TTL; the browser uploads directly to S3/MinIO — bytes never pass through the Course Service heap
- [x] Upload access requires instructor ownership of the course (`course.instructorId.equals(instructorId)`)
- [x] On confirm, the submitted `objectKey` must start with `"lessons/{lessonId}/"` — requests with a key outside this prefix are rejected `400 Bad Request`, preventing path traversal
- [x] Attachment filenames are sanitised to `[a-zA-Z0-9._-]` before being used as the S3 object key suffix
- [x] Presigned GET URLs for lesson content and attachments have a 1-hour TTL; download access requires an active enrolment with the relevant module unlocked
- [x] Instructors can always retrieve content for their own course; learner access is gated by `module_unlocks`

## CORS

- [x] Spring Boot services configured to accept requests from the frontend origin only (not `*`)
- [x] Traefik forwards `Origin` header; responses include correct `Access-Control-Allow-Origin`

## Dependency Security

- [ ] Run `npm audit` / `./mvnw dependency:check` / `pip-audit` before final submission and resolve HIGH/CRITICAL findings
- [ ] Confirm no CVE-flagged transitive dependencies in the Docker base images

---

*Last updated: 2026-05-23*
