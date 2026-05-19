# LearnPulse — Sequence Diagrams

Six end-to-end flows. Each diagram is accurate to the actual implementation.

---

## 1. Authentication Flow

Covers registration, login, and how Traefik ForwardAuth validates every subsequent request.

```mermaid
sequenceDiagram
    actor Browser
    participant Traefik as Traefik<br/>(API Gateway)
    participant US as User Service<br/>:8081
    participant DB as MySQL<br/>(learnpulse_users)
    participant Redis

    %% ── REGISTRATION ──────────────────────────────────────────────
    rect rgb(235, 245, 255)
        Note over Browser,DB: Registration
        Browser->>Traefik: POST /api/auth/register<br/>{ name, email, password, role }
        Note over Traefik: Rate-limit middleware<br/>(10 rpm / IP) — no ForwardAuth
        Traefik->>US: POST /api/auth/register
        US->>DB: SELECT 1 FROM users WHERE email = ?
        DB-->>US: (empty)
        US->>DB: INSERT INTO users (BCrypt password, role=LEARNER [+INSTRUCTOR])
        DB-->>US: OK
        US-->>Traefik: 201 { userId, roles }
        Traefik-->>Browser: 201 { userId, roles }
    end

    %% ── LOGIN ─────────────────────────────────────────────────────
    rect rgb(235, 255, 235)
        Note over Browser,Redis: Login
        Browser->>Traefik: POST /api/auth/login<br/>{ email, password }
        Note over Traefik: Rate-limit only
        Traefik->>US: POST /api/auth/login
        US->>DB: SELECT * FROM users WHERE email = ?
        DB-->>US: User row
        US->>US: BCrypt.matches(password, hash)
        US->>US: Check status != SUSPENDED
        US->>US: JwtService.generateAccessToken(userId, roles)<br/>JwtService.generateRefreshToken(userId)
        US-->>Traefik: 200 { accessToken (15 min), refreshToken (7 days), user }
        Traefik-->>Browser: 200 { accessToken, refreshToken, user }
    end

    %% ── FORWARDAUTH ON EVERY PROTECTED REQUEST ────────────────────
    rect rgb(255, 245, 230)
        Note over Browser,DB: ForwardAuth — every protected API call
        Browser->>Traefik: GET /api/users/me<br/>Authorization: Bearer <accessToken>
        Note over Traefik: JWT ForwardAuth middleware fires FIRST
        Traefik->>US: GET /api/auth/validate<br/>Authorization: Bearer <accessToken>
        US->>US: Parse Bearer token
        US->>US: Verify JWT signature & expiry
        US->>US: Check token is NOT refresh token
        US->>DB: SELECT suspended FROM users WHERE id = ?
        DB-->>US: false
        US-->>Traefik: 200 OK<br/>X-User-Id: <uuid><br/>X-User-Email: user@x.com<br/>X-User-Roles: LEARNER,INSTRUCTOR
        Note over Traefik: Injects headers, forwards original request
        Traefik->>US: GET /api/users/me<br/>X-User-Id / X-User-Email / X-User-Roles
        Note over US: HeaderAuthFilter reads injected headers<br/>→ populates SecurityContext (no JWT needed)
        US->>DB: SELECT * FROM users WHERE id = ?
        DB-->>US: User row
        US-->>Traefik: 200 { profile }
        Traefik-->>Browser: 200 { profile }
    end

    %% ── TOKEN REFRESH ─────────────────────────────────────────────
    rect rgb(245, 235, 255)
        Note over Browser,US: Token Refresh
        Browser->>Traefik: POST /api/auth/refresh<br/>{ refreshToken }
        Traefik->>US: POST /api/auth/refresh
        US->>US: Verify refreshToken signature
        US->>US: Assert token type == REFRESH
        US->>DB: SELECT suspended FROM users WHERE id = ?
        DB-->>US: false
        US->>US: Generate new accessToken + refreshToken
        US-->>Traefik: 200 { accessToken, refreshToken }
        Traefik-->>Browser: 200 { accessToken, refreshToken }
    end
```

---

## 2. Course Publishing Flow

An instructor creates, builds, and publishes a course. On publish, the AI service automatically indexes all lesson content via Kafka.

```mermaid
sequenceDiagram
    actor Instructor as Instructor<br/>(Browser)
    participant Traefik
    participant CS as Course Service<br/>:8080
    participant DB as MySQL<br/>(course_service_db)
    participant Redis
    participant Outbox as Outbox Table<br/>(course_service_db)
    participant Publisher as OutboxPublisher<br/>(every 1 s)
    participant Kafka
    participant AI as AI Service<br/>:9000
    participant MinIO
    participant Chroma as ChromaDB

    %% ── CREATE COURSE ─────────────────────────────────────────────
    rect rgb(235, 245, 255)
        Note over Instructor,DB: Step 1 — Create Course
        Instructor->>Traefik: POST /api/courses<br/>{ title, description, visibility: PRIVATE }
        Traefik->>CS: (ForwardAuth passes X-User-* headers)
        CS->>CS: Generate 8-char enrolment code (PRIVATE courses)
        CS->>DB: INSERT INTO courses (status=DRAFT)
        DB-->>CS: courseId
        CS-->>Traefik: 201 { courseId, enrolmentCode }
        Traefik-->>Instructor: 201 { courseId, enrolmentCode }
    end

    %% ── ADD MODULE + LESSON ───────────────────────────────────────
    rect rgb(235, 255, 235)
        Note over Instructor,DB: Step 2 — Build Course Structure
        Instructor->>Traefik: POST /api/courses/{id}/modules<br/>{ title, orderIndex }
        Traefik->>CS: (ForwardAuth)
        CS->>DB: INSERT INTO modules
        DB-->>CS: moduleId
        CS-->>Traefik: 201 { moduleId }
        Traefik-->>Instructor: 201 { moduleId }

        Instructor->>Traefik: POST /api/courses/{id}/modules/{mId}/lessons<br/>{ title, contentType }
        Traefik->>CS: (ForwardAuth)
        CS->>DB: INSERT INTO lessons
        DB-->>CS: lessonId
        CS-->>Traefik: 201 { lessonId }
        Traefik-->>Instructor: 201 { lessonId }
    end

    %% ── CONTENT UPLOAD ────────────────────────────────────────────
    rect rgb(255, 245, 230)
        Note over Instructor,MinIO: Step 3 — Upload Lesson Content (Two-Step Presigned)
        Instructor->>Traefik: POST /api/courses/{cId}/modules/{mId}/lessons/{lId}/content/upload-url<br/>{ mimeType }
        Traefik->>CS: (ForwardAuth)
        CS->>MinIO: GeneratePresignedPutUrl (15 min TTL)
        MinIO-->>CS: { uploadUrl, objectKey }
        CS-->>Traefik: 200 { uploadUrl, objectKey }
        Traefik-->>Instructor: 200 { uploadUrl, objectKey }

        Instructor->>MinIO: PUT <uploadUrl> (file bytes — direct, bypasses backend)
        MinIO-->>Instructor: 200 OK

        Instructor->>Traefik: POST .../content/confirm { objectKey }
        Traefik->>CS: (ForwardAuth)
        CS->>DB: UPDATE lessons SET content_key = objectKey
        CS-->>Traefik: 204 No Content
        Traefik-->>Instructor: 204 No Content
    end

    %% ── PUBLISH ───────────────────────────────────────────────────
    rect rgb(245, 235, 255)
        Note over Instructor,Chroma: Step 4 — Publish & AI Indexing
        Instructor->>Traefik: POST /api/courses/{id}/publish
        Traefik->>CS: (ForwardAuth)
        CS->>DB: SELECT modules + lessons (validate ≥1 module, ≥1 lesson each)
        DB-->>CS: OK
        CS->>DB: UPDATE courses SET status=PUBLISHED, publishedAt=now()
        CS->>Outbox: INSERT OutboxEvent(topic=course.published, status=PENDING)<br/>payload: { courseId, lessons[{lessonId, title, contentKey, ...}] }
        CS->>Redis: CACHE EVICT courses:{id} + courses:list.*
        CS-->>Traefik: 200 { course summary }
        Traefik-->>Instructor: 200 { course summary }

        Note over Publisher,Kafka: ~1 second later — OutboxPublisher tick
        Publisher->>DB: SELECT TOP 20 outbox WHERE status=PENDING ORDER BY createdAt
        DB-->>Publisher: [course.published event]
        Publisher->>Kafka: ProducerRecord(topic=course.published, payload)
        Kafka-->>Publisher: ACK
        Publisher->>DB: UPDATE outbox SET status=SENT

        Kafka->>AI: course.published consumer (aiokafka)
        Note over AI: For each lesson in payload
        AI->>MinIO: Fetch lesson content bytes (contentKey)
        MinIO-->>AI: Raw file bytes
        alt VIDEO lesson
            AI->>AI: Groq Whisper API → transcribed text
        else ARTICLE / DOCUMENT
            AI->>AI: extract_text(bytes) → plain text
        end
        AI->>AI: Chunk text → embed via sentence-transformers
        AI->>Chroma: upsert(ids, embeddings, metadata, documents)<br/>namespace = courseId
        Chroma-->>AI: OK
        Note over AI: Repeat for all lessons
    end
```

---

## 3. Enrolment Flow

A learner finds and enrols in a course. An async email is delivered via Kafka.

```mermaid
sequenceDiagram
    actor Learner as Learner<br/>(Browser)
    participant Traefik
    participant CS as Course Service<br/>:8080
    participant DB as MySQL<br/>(course_service_db)
    participant Redis
    participant Outbox as Outbox Table
    participant Publisher as OutboxPublisher
    participant Kafka
    participant US as User Service<br/>(Email Consumer)
    participant UDB as MySQL<br/>(learnpulse_users)
    participant Mailgun

    %% ── PUBLIC ENROLMENT ──────────────────────────────────────────
    rect rgb(235, 245, 255)
        Note over Learner,Mailgun: Public Course Enrolment
        Learner->>Traefik: POST /api/enrolments<br/>{ courseId }
        Traefik->>CS: (ForwardAuth → X-User-Id injected)
        CS->>DB: SELECT * FROM courses WHERE id = ? AND status = PUBLISHED
        DB-->>CS: Course (visibility=PUBLIC)
        CS->>DB: SELECT 1 FROM enrolments WHERE userId=? AND courseId=?
        DB-->>CS: (empty — not enrolled yet)
        CS->>DB: INSERT INTO enrolments (status=ACTIVE, startedAt=null)
        CS->>Outbox: INSERT OutboxEvent(topic=user.enrolled)<br/>{ eventId, userId, courseId, enrolmentId }
        CS->>Redis: CACHE EVICT analytics:instructor.*, analytics:admin:platform
        CS-->>Traefik: 201 { enrolmentId, courseId, status: ACTIVE }
        Traefik-->>Learner: 201 { enrolment }
    end

    %% ── PRIVATE COURSE ────────────────────────────────────────────
    rect rgb(255, 245, 230)
        Note over Learner,CS: Private Course (with enrolment code)
        Learner->>Traefik: POST /api/enrolments<br/>{ courseId, enrolmentCode: "ABC12345" }
        Traefik->>CS: (ForwardAuth)
        CS->>DB: SELECT * FROM courses WHERE id = ?
        DB-->>CS: Course (visibility=PRIVATE, enrolmentCode="ABC12345")
        CS->>CS: Assert request.enrolmentCode == course.enrolmentCode
        CS->>DB: INSERT INTO enrolments (status=ACTIVE)
        CS->>Outbox: INSERT OutboxEvent(topic=user.enrolled)
        CS-->>Traefik: 201 { enrolment }
        Traefik-->>Learner: 201 { enrolment }
    end

    %% ── ASYNC EMAIL ───────────────────────────────────────────────
    rect rgb(235, 255, 235)
        Note over Publisher,Mailgun: Async — Enrolment Email (~1 second delay)
        Publisher->>DB: SELECT TOP 20 outbox WHERE status=PENDING
        DB-->>Publisher: [user.enrolled event]
        Publisher->>Kafka: ProducerRecord(topic=user.enrolled)
        Kafka-->>Publisher: ACK
        Publisher->>DB: UPDATE outbox SET status=SENT

        Kafka->>US: user.enrolled consumer (group: email-service)
        US->>UDB: SELECT * FROM idempotency_log WHERE eventId = ?
        UDB-->>US: (empty)
        US->>US: Build welcome email<br/>(learner name, course title, instructor name, course link)
        US->>Mailgun: POST /messages (welcome email)
        Mailgun-->>US: 200 OK
        US->>UDB: INSERT INTO idempotency_log (eventId, topic)
        US-->>Kafka: ACK offset
    end
```

---

## 4. Learning Flow

A learner starts a course, completes lessons, unlocks modules, and finishes the course.

```mermaid
sequenceDiagram
    actor Learner as Learner<br/>(Browser)
    participant Traefik
    participant CS as Course Service<br/>:8080
    participant DB as MySQL<br/>(course_service_db)
    participant Redis
    participant Outbox as Outbox Table
    participant Publisher as OutboxPublisher
    participant Kafka
    participant US as User Service<br/>(Email Consumer)
    participant Mailgun

    %% ── START COURSE ──────────────────────────────────────────────
    rect rgb(235, 245, 255)
        Note over Learner,DB: Start Course (Module 1 unlocked)
        Learner->>Traefik: POST /api/enrolments/{id}/start
        Traefik->>CS: (ForwardAuth)
        CS->>DB: SELECT enrolment WHERE id=? AND userId=?
        DB-->>CS: Enrolment (startedAt=null)
        CS->>DB: UPDATE enrolments SET startedAt=now()
        CS->>DB: UPDATE courses SET locked=true, lockedAt=now()
        CS->>DB: INSERT INTO module_unlocks (enrolmentId, moduleId=module1)
        DB-->>CS: OK
        CS-->>Traefik: 200 { startedAt, firstModuleId }
        Traefik-->>Learner: 200 { startedAt, firstModuleId }
    end

    %% ── COMPLETE LESSON (MID-MODULE) ──────────────────────────────
    rect rgb(235, 255, 235)
        Note over Learner,DB: Complete a lesson (not the last in its module)
        Learner->>Traefik: POST /api/lessons/{lessonId}/complete
        Traefik->>CS: (ForwardAuth)
        CS->>DB: SELECT lesson, module, course
        CS->>DB: SELECT enrolment WHERE userId=? AND courseId=?
        CS->>DB: SELECT module_unlock WHERE enrolmentId=? AND moduleId=?
        DB-->>CS: Module IS unlocked
        CS->>DB: SELECT lesson_progress for all previous lessons in module (by orderIndex)
        DB-->>CS: All prior lessons completed ✓
        CS->>DB: SELECT lesson_progress WHERE userId=? AND lessonId=?
        DB-->>CS: (empty — not yet completed)
        CS->>DB: INSERT INTO lesson_progress (status=COMPLETED, completedAt=now())
        CS->>DB: Is this the last lesson in the module? → NO
        CS->>Redis: CACHE EVICT analytics:instructor.*, analytics:admin:platform
        CS-->>Traefik: 200 { lessonId, completedAt, nextModuleId: null, courseCompleted: false }
        Traefik-->>Learner: 200 { progress }
    end

    %% ── COMPLETE LAST LESSON IN MODULE → UNLOCK NEXT ─────────────
    rect rgb(255, 245, 230)
        Note over Learner,Mailgun: Complete last lesson in module → unlock next module
        Learner->>Traefik: POST /api/lessons/{lastLessonInModule}/complete
        Traefik->>CS: (ForwardAuth)
        CS->>DB: INSERT INTO lesson_progress (COMPLETED)
        CS->>DB: Is this the last lesson in module? → YES
        CS->>DB: SELECT next module by orderIndex → Module 2 found
        CS->>DB: INSERT INTO module_unlocks (enrolmentId, moduleId=module2)
        CS->>Outbox: INSERT OutboxEvent(topic=module.unlocked)<br/>{ eventId, userId, courseId, unlockedModuleId, unlockedModuleTitle }
        CS->>Redis: CACHE EVICT analytics
        CS-->>Traefik: 200 { lessonId, completedAt, nextModuleId: module2Id, courseCompleted: false }
        Traefik-->>Learner: 200 { progress }

        Note over Publisher,Mailgun: Async — Module Unlock Email
        Publisher->>Kafka: ProducerRecord(topic=module.unlocked)
        Kafka->>US: module.unlocked consumer
        US->>US: Idempotency check (eventId not seen)
        US->>US: Build "Your next module is ready" email
        US->>Mailgun: POST /messages
        Mailgun-->>US: 200 OK
        US->>US: INSERT idempotency_log
        US-->>Kafka: ACK
    end

    %% ── COMPLETE FINAL LESSON → COURSE COMPLETE ───────────────────
    rect rgb(245, 235, 255)
        Note over Learner,Kafka: Complete final lesson → course completion
        Learner->>Traefik: POST /api/lessons/{finalLesson}/complete
        Traefik->>CS: (ForwardAuth)
        CS->>DB: INSERT INTO lesson_progress (COMPLETED)
        CS->>DB: Is this the last lesson in last module? → YES, no next module
        CS->>DB: UPDATE enrolments SET status=COMPLETED, completedAt=now()
        CS->>Outbox: INSERT OutboxEvent(topic=course.completed)<br/>{ eventId, userId, courseId, enrolmentId, completedAt }
        CS->>Redis: CACHE EVICT analytics
        CS-->>Traefik: 200 { lessonId, completedAt, nextModuleId: null, courseCompleted: true }
        Traefik-->>Learner: 200 { courseCompleted: true }
    end

    %% ── VIEW PROGRESS ─────────────────────────────────────────────
    rect rgb(240, 255, 240)
        Note over Learner,DB: View detailed progress at any time
        Learner->>Traefik: GET /api/enrolments/{id}/progress
        Traefik->>CS: (ForwardAuth)
        CS->>DB: SELECT enrolment (verify ownership)
        CS->>DB: SELECT all module_unlocks for enrolmentId
        CS->>DB: SELECT all lesson_progress for userId + lessonIds in course
        CS->>CS: Compute: current lesson, % complete, module tree with statuses
        CS-->>Traefik: 200 { progressPercent, currentLesson, modules[{status, lessons[{status}]}] }
        Traefik-->>Learner: 200 { progress }
    end
```

---

## 5. Certificate Generation Flow

Triggered automatically when a learner completes their final lesson. The system guarantees exactly-once PDF issuance via two-layer idempotency.

```mermaid
sequenceDiagram
    participant Kafka
    participant CertC as CertificateConsumer<br/>(cert-service)
    participant CertSvc as CertificateService
    participant CertDB as MySQL<br/>(learnpulse_certs)
    participant USC as UserServiceClient<br/>(REST)
    participant CSC as CourseServiceClient<br/>(REST)
    participant US as User Service<br/>:8081
    participant CoS as Course Service<br/>:8080
    participant PDF as PdfService<br/>(Thymeleaf + Flying Saucer)
    participant S3 as MinIO / S3
    participant Outbox as Outbox Table<br/>(learnpulse_certs)
    participant Publisher as OutboxPublisher<br/>(cert-service)
    participant EmailKafka as Kafka
    participant EmailUS as User Service<br/>(Email Consumer)
    participant Mailgun
    actor Learner

    %% ── CONSUME COURSE.COMPLETED ──────────────────────────────────
    rect rgb(235, 245, 255)
        Note over Kafka,CertDB: Layer 1 — Idempotency Check
        Kafka->>CertC: course.completed<br/>{ eventId, userId, courseId, enrolmentId, completedAt }
        CertC->>CertSvc: issue(CourseCompletedEvent)
        CertSvc->>CertDB: SELECT 1 FROM idempotency_log WHERE eventId = ?
        CertDB-->>CertSvc: (empty — first time)
    end

    %% ── FETCH EXTERNAL DATA ───────────────────────────────────────
    rect rgb(235, 255, 235)
        Note over CertSvc,CoS: Fetch learner + course details
        CertSvc->>USC: GET http://user-service:8081/internal/users/{userId}
        USC->>US: Internal REST call
        US-->>USC: { fullName, email }
        USC-->>CertSvc: UserInfo

        CertSvc->>CSC: GET http://course-service:8080/internal/courses/{courseId}
        CSC->>CoS: Internal REST call
        CoS-->>CSC: { title, instructorId }
        CSC-->>CertSvc: CourseInfo

        CertSvc->>USC: GET http://user-service:8081/internal/users/{instructorId}
        USC->>US: Internal REST call
        US-->>USC: { fullName }
        USC-->>CertSvc: InstructorInfo
    end

    %% ── GENERATE PDF ──────────────────────────────────────────────
    rect rgb(255, 245, 230)
        Note over CertSvc,S3: Generate PDF and upload to S3
        CertSvc->>CertSvc: Generate certUuid = UUID.randomUUID()
        CertSvc->>PDF: generateCertificate(learnerName, courseTitle,<br/>instructorName, certUuid, issuedDate)
        Note over PDF: Thymeleaf renders HTML template<br/>Flying Saucer converts HTML → PDF bytes
        PDF-->>CertSvc: byte[] pdfBytes

        CertSvc->>S3: PUT certificates/{userId}/{courseId}/{certUuid}.pdf
        S3-->>CertSvc: OK

        CertSvc->>S3: generatePresignedGetUrl(key, 7 days)
        S3-->>CertSvc: downloadUrl
    end

    %% ── ATOMIC COMMIT ─────────────────────────────────────────────
    rect rgb(245, 235, 255)
        Note over CertSvc,Outbox: Single DB transaction (exactly-once guarantee)
        CertSvc->>CertDB: BEGIN TRANSACTION
        CertSvc->>CertDB: INSERT INTO certificates<br/>(userId, courseId, s3Key, learnerName, courseName)<br/>UK(userId, courseId) → duplicate throws DataIntegrityViolationException
        CertSvc->>CertDB: INSERT INTO idempotency_log (eventId, "course.completed")
        CertSvc->>Outbox: INSERT OutboxEvent(topic=certificate.generated)<br/>{ eventId, userId, courseId, certificateId, s3Key, downloadUrl, issuedAt }
        CertSvc->>CertDB: COMMIT
        CertDB-->>CertSvc: OK
        CertC-->>Kafka: ACK offset
    end

    %% ── DUPLICATE GUARD ───────────────────────────────────────────
    rect rgb(255, 235, 235)
        Note over Kafka,CertC: If Kafka redelivers the same event (crash before ACK)
        Kafka->>CertC: course.completed (same eventId — redelivery)
        CertC->>CertSvc: issue(event)
        CertSvc->>CertDB: SELECT 1 FROM idempotency_log WHERE eventId = ?
        CertDB-->>CertSvc: FOUND
        CertSvc-->>CertC: return null (skip)
        CertC-->>Kafka: ACK (discard duplicate)
    end

    %% ── CERTIFICATE EMAIL ─────────────────────────────────────────
    rect rgb(235, 255, 250)
        Note over Publisher,Mailgun: Async — Certificate Delivery Email
        Publisher->>EmailKafka: ProducerRecord(topic=certificate.generated)
        EmailKafka-->>Publisher: ACK
        Publisher->>Outbox: UPDATE status=SENT

        EmailKafka->>EmailUS: certificate.generated consumer (group: email-service)
        EmailUS->>EmailUS: Idempotency check (eventId not seen)
        EmailUS->>EmailUS: Build congratulatory email<br/>(learner name, course name, PDF download link)
        EmailUS->>Mailgun: POST /messages
        Mailgun-->>EmailUS: 200 OK
        EmailUS->>EmailUS: INSERT idempotency_log
        EmailUS-->>EmailKafka: ACK
    end

    %% ── LEARNER DOWNLOADS CERTIFICATE ────────────────────────────
    rect rgb(240, 248, 255)
        Note over Learner,S3: Learner downloads certificate from dashboard
        Learner->>Traefik: GET /api/learner/certificates
        Traefik->>CertC: (ForwardAuth)
        Note over CertC: CertificateController
        CertC->>CertDB: SELECT * FROM certificates WHERE userId = ?
        CertDB-->>CertC: [{ certUuid, courseName, issuedAt, s3Key }]
        CertC-->>Traefik: 200 [{ certUuid, courseName, issuedAt }]
        Traefik-->>Learner: 200 [{ certificates }]

        Learner->>Traefik: GET /api/certificates/{uuid}/download
        Traefik->>CertC: (ForwardAuth)
        CertC->>CertDB: SELECT s3Key WHERE uuid=? AND userId=?
        CertC->>S3: generatePresignedGetUrl(key, 1 hour)
        S3-->>CertC: presignedUrl
        CertC-->>Traefik: 302 Redirect → presignedUrl
        Traefik-->>Learner: 302 → S3 presigned URL
        Learner->>S3: GET <presignedUrl>
        S3-->>Learner: PDF bytes
    end
```

---

## 6. AI Study Assistant Flow

Triggered when a learner opens the AI chat tab inside a course they have started.

```mermaid
sequenceDiagram
    actor Learner as Learner<br/>(Browser)
    participant Traefik
    participant CS as Course Service<br/>:8080
    participant AI as AI Service<br/>:9000
    participant Redis
    participant Chroma as ChromaDB
    participant Cerebras as Cerebras API<br/>(Llama 3.1 8B)

    %% ── CREATE CHAT SESSION ───────────────────────────────────────
    rect rgb(235, 245, 255)
        Note over Learner,Redis: Open AI Chat — Create Session
        Learner->>Traefik: POST /api/ai/courses/{courseId}/sessions
        Note over Traefik: ForwardAuth validates JWT<br/>injects X-User-Id, X-User-Roles
        Traefik->>AI: POST /api/ai/courses/{courseId}/sessions<br/>X-User-Id: <uuid>

        AI->>CS: GET http://course-service:8080/internal/courses/{courseId}/enrolment<br/>X-Service-Auth: <secret> (service-to-service)
        Note over CS: InternalEnrolmentController<br/>verifies user has ACTIVE enrolment with startedAt set
        CS-->>AI: 200 { courseTitle }

        AI->>AI: sessionId = UUID.randomUUID()
        AI->>Redis: SET session:{sessionId} → { userId, courseId, courseTitle, history: [] }<br/>TTL: 1 hour
        Redis-->>AI: OK
        AI-->>Traefik: 201 { sessionId }
        Traefik-->>Learner: 201 { sessionId }
    end

    %% ── SEND MESSAGE (RAG CHAT) ───────────────────────────────────
    rect rgb(235, 255, 235)
        Note over Learner,Cerebras: Send Message — RAG + Streaming Response
        Learner->>Traefik: POST /api/ai/courses/{courseId}/sessions/{sessionId}/messages<br/>{ message: "What does Module 2 cover?" }
        Traefik->>AI: (ForwardAuth — X-User-Id injected)

        AI->>Redis: GET session:{sessionId}
        Redis-->>AI: { userId, courseId, history: [...] }
        AI->>AI: Assert session.userId == X-User-Id (ownership check)

        Note over AI,Chroma: Vector Retrieval
        AI->>AI: Embed query message → query vector
        AI->>Chroma: query(queryVector, where={courseId: courseId}, nResults=6)
        Chroma-->>AI: Top-K relevant lesson chunks<br/>[ { text, lessonId, lessonTitle, moduleTitle, score } ]

        Note over AI,Cerebras: LLM Generation
        AI->>AI: Build prompt:<br/>System: "You are a study assistant for {courseTitle}.<br/>Answer ONLY from the provided context."<br/>Context: [retrieved chunks]<br/>History: [previous turns]<br/>Human: "What does Module 2 cover?"

        AI->>Cerebras: ChatCerebras.stream(messages, model="llama-3.1-8b")
        Note over AI,Learner: Server-Sent Events stream opens (text/event-stream)
        loop For each token in stream
            Cerebras-->>AI: token chunk
            AI-->>Learner: data: {"token": "Module"}\n\n
        end
        Cerebras-->>AI: [stream complete]
        AI-->>Learner: data: [DONE]\n\n

        AI->>Redis: APPEND to session history:<br/>{ role: user, content: message }<br/>{ role: assistant, content: fullResponse }
        Redis-->>AI: OK
    end

    %% ── RATE LIMIT / ERROR HANDLING ──────────────────────────────
    rect rgb(255, 245, 230)
        Note over AI,Learner: Error Handling
        alt Cerebras rate limit hit (RateLimitError)
            AI-->>Learner: data: {"token": "The AI assistant is busy — please try again in a moment."}\n\n
            AI-->>Learner: data: [DONE]\n\n
        else Session not found or expired
            AI-->>Traefik: 404 { error: "Session not found or expired" }
            Traefik-->>Learner: 404
        else User does not own session
            AI-->>Traefik: 403 { error: "Forbidden" }
            Traefik-->>Learner: 403
        end
    end
```

---

## Architecture Summary

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           Request Flows at a Glance                           │
├────────────────────────┬────────────────────────────────────────────────────┤
│ Auth (register/login)  │ Browser → Traefik (rate-limit) → User Service      │
│ Any protected endpoint │ Browser → Traefik → ForwardAuth → User Service     │
│                        │        → inject X-User-* headers → target service  │
│ Course CRUD / enrol    │ Traefik → Course Service → MySQL + Redis + Outbox   │
│ Certificate            │ Kafka (course.completed) → Cert Service → S3       │
│                        │ → Kafka (cert.generated) → User Service → Mailgun  │
│ AI chat                │ Traefik → AI Service → Course Service (enrolment)  │
│                        │        → ChromaDB (retrieval) → Cerebras (LLM)     │
│ Kafka events           │ All produced via Outbox pattern (at-least-once)     │
│                        │ All consumed with eventId idempotency (exactly-once)│
└────────────────────────┴────────────────────────────────────────────────────┘
```