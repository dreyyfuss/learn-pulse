# LearnPulse API Testing Guide

This guide covers every HTTP endpoint across the three LearnPulse microservices. All examples use `curl`; substitute your own UUIDs and tokens where placeholders appear.

---

## Base URLs

| Service        | Local URL                   | Swagger UI                              |
|----------------|-----------------------------|-----------------------------------------|
| user-service   | `http://localhost:8081`     | http://localhost:8081/swagger-ui.html   |
| course-service | `http://localhost:8080`     | http://localhost:8080/swagger-ui.html   |
| cert-service   | `http://localhost:8082`     | http://localhost:8082/swagger-ui.html   |

---

## Authentication

### How it works

1. **user-service** is the identity provider. It issues a JWT access token and a refresh token on login.
2. **course-service** and **cert-service** validate the JWT via a shared secret. They also accept three plain headers for **local / manual testing** without a running user-service:

| Header          | Example value                          | Purpose                          |
|-----------------|----------------------------------------|----------------------------------|
| `X-User-Id`     | `550e8400-e29b-41d4-a716-446655440000` | Authenticated user's UUID        |
| `X-User-Roles`  | `ROLE_LEARNER` or `ROLE_INSTRUCTOR`    | Comma-separated Spring roles     |
| `X-User-Email`  | `alice@example.com`                    | User email (used by cert-service)|

> Use the header approach only in local development. Never send these headers to a production deployment.

### POST /api/auth/register

Register a new account.

```bash
curl -s -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Alice Learner",
    "email": "alice@example.com",
    "password": "Secret123!",
    "registerAsInstructor": false
  }'
```

Expected: `201 Created`

### POST /api/auth/login

Obtain a JWT access token.

```bash
curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "Secret123!"
  }'
```

Expected: `200 OK` — response body contains `accessToken` and `refreshToken`.

Save the token for subsequent requests:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Secret123!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
```

Then pass it as a Bearer token:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/courses
```

### POST /api/auth/refresh

Exchange a refresh token for a new access token.

```bash
curl -s -X POST http://localhost:8081/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<your-refresh-token>"
  }'
```

Expected: `200 OK`

### POST /api/auth/logout

Invalidate the current refresh token.

```bash
curl -s -X POST http://localhost:8081/api/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200 OK`

---

## user-service (port 8081)

### Auth

#### POST /api/auth/register
See [Authentication](#authentication) section above.

#### POST /api/auth/login
See [Authentication](#authentication) section above.

#### POST /api/auth/refresh
See [Authentication](#authentication) section above.

#### POST /api/auth/logout
See [Authentication](#authentication) section above.

---

### Users

#### GET /api/users/me

Returns the authenticated user's profile.

- **Role required:** any authenticated user

```bash
curl -s http://localhost:8081/api/users/me \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200 OK`

#### PATCH /api/users/me

Update display name or password. Both fields are optional.

- **Role required:** any authenticated user

```bash
curl -s -X PATCH http://localhost:8081/api/users/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Alice Updated",
    "password": "NewSecret456!"
  }'
```

Expected: `200 OK`

---

### Admin — Users

All admin endpoints require the `ADMIN` role.

#### GET /api/admin/users

List all users (paginated).

```bash
curl -s "http://localhost:8081/api/admin/users?page=0&size=20" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected: `200 OK`

#### GET /api/admin/users/{userId}

Fetch a single user by UUID.

```bash
curl -s http://localhost:8081/api/admin/users/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected: `200 OK`

#### PATCH /api/admin/users/{userId}

Update any user's profile or role.

```bash
curl -s -X PATCH http://localhost:8081/api/admin/users/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Alice Admin",
    "password": "AdminSet789!"
  }'
```

Expected: `200 OK`

#### DELETE /api/admin/users/{userId}

Delete a user account.

```bash
curl -s -X DELETE http://localhost:8081/api/admin/users/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected: `204 No Content`

---

### Health

#### GET /actuator/health

```bash
curl -s http://localhost:8081/actuator/health
```

Expected: `200 OK` — `{"status":"UP"}`

---

## course-service (port 8080)

For local testing without a running user-service, replace `-H "Authorization: Bearer $TOKEN"` with:

```bash
-H "X-User-Id: 550e8400-e29b-41d4-a716-446655440000" \
-H "X-User-Roles: ROLE_LEARNER" \
-H "X-User-Email: alice@example.com"
```

Use `ROLE_INSTRUCTOR` for instructor endpoints, `ROLE_ADMIN` for admin endpoints.

---

### Courses

#### GET /api/courses

Browse published courses. Supports optional query params: `page`, `size`, `search`, `category`.

- **Role required:** any authenticated user

```bash
curl -s "http://localhost:8080/api/courses?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200 OK`

#### POST /api/courses

Create a new course.

- **Role required:** `INSTRUCTOR`
- `visibility` must be one of: `PUBLIC`, `PRIVATE`, `DRAFT`

```bash
curl -s -X POST http://localhost:8080/api/courses \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Introduction to Spring Boot",
    "description": "A beginner-friendly course on Spring Boot microservices.",
    "thumbnailUrl": "https://example.com/thumb.jpg",
    "category": "Backend Development",
    "visibility": "DRAFT"
  }'
```

Expected: `201 Created`

#### GET /api/courses/{courseId}

Fetch a single course by UUID.

- **Role required:** any authenticated user

```bash
curl -s http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200 OK`

#### PATCH /api/courses/{courseId}

Update course metadata (instructor-owner only).

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X PATCH http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Spring Boot Advanced",
    "visibility": "PUBLIC"
  }'
```

Expected: `200 OK`

#### DELETE /api/courses/{courseId}

Delete a course (instructor-owner only).

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X DELETE http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN"
```

Expected: `204 No Content`

#### POST /api/courses/generate

AI-powered course builder — generates a course outline from a text prompt.

- **Role required:** `INSTRUCTOR`
- `prompt` must be at least 10 characters

```bash
curl -s -X POST http://localhost:8080/api/courses/generate \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Create a 5-module course on Docker and Kubernetes for backend developers."
  }'
```

Expected: `201 Created`

---

### Enrolments

#### POST /api/enrolments

Enrol the authenticated learner in a course.

- **Role required:** `LEARNER`
- `enrolmentCode` is optional (required only for private courses that use a code)

```bash
curl -s -X POST http://localhost:8080/api/enrolments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "courseId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
    "enrolmentCode": ""
  }'
```

Expected: `201 Created`

#### GET /api/enrolments

List the authenticated learner's enrolments.

- **Role required:** `LEARNER`

```bash
curl -s http://localhost:8080/api/enrolments \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200 OK`

#### DELETE /api/enrolments/{enrolmentId}

Unenrol from a course.

- **Role required:** `LEARNER`

```bash
curl -s -X DELETE http://localhost:8080/api/enrolments/11111111-2222-3333-4444-555555555555 \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `204 No Content`

---

### Learner

#### GET /api/learner/courses/{courseId}

Get course content for an enrolled learner (modules, lessons, progress).

- **Role required:** `LEARNER`

```bash
curl -s http://localhost:8080/api/learner/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200 OK`

---

### Modules

All module endpoints are scoped to a course and require `INSTRUCTOR` ownership.

#### GET /api/courses/{courseId}/modules

List modules for a course.

- **Role required:** `INSTRUCTOR`

```bash
curl -s http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN"
```

Expected: `200 OK`

#### POST /api/courses/{courseId}/modules

Create a module.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X POST http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Module 1: Getting Started",
    "orderIndex": 1
  }'
```

Expected: `201 Created`

#### PATCH /api/courses/{courseId}/modules/{moduleId}

Update a module's title.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X PATCH http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Module 1: Introduction"
  }'
```

Expected: `200 OK`

#### DELETE /api/courses/{courseId}/modules/{moduleId}

Delete a module.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X DELETE http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN"
```

Expected: `204 No Content`

#### PUT /api/courses/{courseId}/modules/reorder

Reorder modules within a course.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X PUT http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/reorder \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "modules": [
      {"id": "bbbbbbbb-cccc-dddd-eeee-ffffffffffff", "orderIndex": 1},
      {"id": "cccccccc-dddd-eeee-ffff-000000000000", "orderIndex": 2}
    ]
  }'
```

Expected: `204 No Content`

---

### Lessons

#### GET /api/courses/{courseId}/modules/{moduleId}/lessons

List lessons in a module.

- **Role required:** `INSTRUCTOR`

```bash
curl -s http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/lessons \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN"
```

Expected: `200 OK`

#### POST /api/courses/{courseId}/modules/{moduleId}/lessons

Create a lesson.

- **Role required:** `INSTRUCTOR`
- `contentType` must be one of: `VIDEO`, `DOCUMENT`, `ARTICLE`, `OTHER`

```bash
curl -s -X POST http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/lessons \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Lesson 1: Setting Up Your Environment",
    "contentType": "VIDEO",
    "orderIndex": 1
  }'
```

Expected: `201 Created`

#### GET /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}

Get lesson details.

- **Role required:** `INSTRUCTOR`

```bash
curl -s http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/lessons/dddddddd-eeee-ffff-0000-111111111111 \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN"
```

Expected: `200 OK`

#### PATCH /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}

Update a lesson.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X PATCH http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/lessons/dddddddd-eeee-ffff-0000-111111111111 \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Lesson 1: Installation & Setup"
  }'
```

Expected: `200 OK`

#### DELETE /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}

Delete a lesson.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X DELETE http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/lessons/dddddddd-eeee-ffff-0000-111111111111 \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN"
```

Expected: `204 No Content`

#### PUT /api/courses/{courseId}/modules/{moduleId}/lessons/reorder

Reorder lessons within a module.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X PUT http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/lessons/reorder \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "lessons": [
      {"id": "dddddddd-eeee-ffff-0000-111111111111", "orderIndex": 1},
      {"id": "eeeeeeee-ffff-0000-1111-222222222222", "orderIndex": 2}
    ]
  }'
```

Expected: `204 No Content`

---

### Lesson Content

#### POST /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/content/upload-url

Request a presigned S3 upload URL for the lesson's primary content file.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X POST http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/lessons/dddddddd-eeee-ffff-0000-111111111111/content/upload-url \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "mimeType": "video/mp4"
  }'
```

Expected: `200 OK` — response contains `uploadUrl` and `objectKey`.

#### POST /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/content/confirm

Confirm that the content upload to S3 completed successfully.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X POST http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/lessons/dddddddd-eeee-ffff-0000-111111111111/content/confirm \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "objectKey": "courses/aaaaaaaa/lessons/dddddddd/video.mp4"
  }'
```

Expected: `200 OK`

#### POST /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/attachments/upload-url

Request a presigned S3 upload URL for a lesson attachment.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X POST http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/lessons/dddddddd-eeee-ffff-0000-111111111111/attachments/upload-url \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "slides.pdf",
    "mimeType": "application/pdf"
  }'
```

Expected: `200 OK`

#### POST /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/attachments/confirm

Confirm an attachment upload.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X POST http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/lessons/dddddddd-eeee-ffff-0000-111111111111/attachments/confirm \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "objectKey": "courses/aaaaaaaa/lessons/dddddddd/slides.pdf",
    "fileName": "slides.pdf",
    "mimeType": "application/pdf"
  }'
```

Expected: `200 OK`

#### DELETE /api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/attachments/{attachmentId}

Delete a lesson attachment.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X DELETE http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/lessons/dddddddd-eeee-ffff-0000-111111111111/attachments/ffffffff-0000-1111-2222-333333333333 \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN"
```

Expected: `204 No Content`

---

### Lesson Progress

#### POST /api/learner/lessons/{lessonId}/complete

Mark a lesson as completed for the authenticated learner.

- **Role required:** `LEARNER`

```bash
curl -s -X POST http://localhost:8080/api/learner/lessons/dddddddd-eeee-ffff-0000-111111111111/complete \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200 OK`

---

### Quizzes

Quiz management endpoints are for instructors. They are nested under a course and module.

#### POST /api/courses/{courseId}/modules/{moduleId}/quizzes

Create a quiz.

- **Role required:** `INSTRUCTOR`
- `passingScore` must be between 0 and 100 (optional)

```bash
curl -s -X POST http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/quizzes \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Chapter 1 Quiz",
    "description": "Test your knowledge of chapter 1.",
    "passingScore": 70,
    "orderIndex": 1
  }'
```

Expected: `201 Created`

#### GET /api/courses/{courseId}/modules/{moduleId}/quizzes/{quizId}

Get a quiz with its questions (instructor view).

- **Role required:** `INSTRUCTOR`

```bash
curl -s http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/quizzes/qqqqqqqq-wwww-eeee-rrrr-tttttttttttt \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN"
```

Expected: `200 OK`

#### PATCH /api/courses/{courseId}/modules/{moduleId}/quizzes/{quizId}

Update quiz metadata.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X PATCH http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/quizzes/qqqqqqqq-wwww-eeee-rrrr-tttttttttttt \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Chapter 1 Knowledge Check",
    "passingScore": 75
  }'
```

Expected: `200 OK`

#### DELETE /api/courses/{courseId}/modules/{moduleId}/quizzes/{quizId}

Delete a quiz.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X DELETE http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/quizzes/qqqqqqqq-wwww-eeee-rrrr-tttttttttttt \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN"
```

Expected: `204 No Content`

#### PUT /api/courses/{courseId}/modules/{moduleId}/quizzes/reorder

Reorder quizzes within a module.

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X PUT http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/quizzes/reorder \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "quizzes": [
      {"id": "qqqqqqqq-wwww-eeee-rrrr-tttttttttttt", "orderIndex": 1},
      {"id": "11111111-2222-3333-4444-555555555555", "orderIndex": 2}
    ]
  }'
```

Expected: `204 No Content`

#### PUT /api/courses/{courseId}/modules/{moduleId}/quizzes/{quizId}/questions

Create or replace all questions in a quiz (upsert). `questionType` values depend on the `QuestionType` enum (typically `SINGLE_CHOICE`).

- **Role required:** `INSTRUCTOR`

```bash
curl -s -X PUT http://localhost:8080/api/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/modules/bbbbbbbb-cccc-dddd-eeee-ffffffffffff/quizzes/qqqqqqqq-wwww-eeee-rrrr-tttttttttttt/questions \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "questions": [
      {
        "questionText": "What annotation marks a Spring Boot entry point?",
        "questionType": "SINGLE_CHOICE",
        "options": [
          {"optionText": "@SpringBootApplication", "isCorrect": true},
          {"optionText": "@EnableAutoConfig",       "isCorrect": false},
          {"optionText": "@ComponentScan",          "isCorrect": false},
          {"optionText": "@RunWith",                "isCorrect": false}
        ]
      }
    ]
  }'
```

Expected: `200 OK`

---

### Quiz Attempts

Attempt endpoints are accessed by learners at `/api/quizzes/{quizId}`.

#### GET /api/quizzes/{quizId}/player

Get the quiz in player mode (questions without revealing correct answers).

- **Role required:** `LEARNER`

```bash
curl -s http://localhost:8080/api/quizzes/qqqqqqqq-wwww-eeee-rrrr-tttttttttttt/player \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200 OK`

#### POST /api/quizzes/{quizId}/attempts

Submit a quiz attempt. `answers` is a map of `questionId` to `selectedOptionId`.

- **Role required:** `LEARNER`

```bash
curl -s -X POST http://localhost:8080/api/quizzes/qqqqqqqq-wwww-eeee-rrrr-tttttttttttt/attempts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "answers": {
      "aaaaaaaa-0001-0001-0001-000000000001": "aaaaaaaa-0002-0002-0002-000000000002"
    }
  }'
```

Expected: `200 OK` — response includes score and pass/fail result.

#### GET /api/quizzes/{quizId}/attempts/best

Retrieve the learner's highest-scoring attempt for a quiz.

- **Role required:** `LEARNER`

```bash
curl -s http://localhost:8080/api/quizzes/qqqqqqqq-wwww-eeee-rrrr-tttttttttttt/attempts/best \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200 OK`

---

### Instructor

#### GET /api/instructor/courses

List all courses owned by the authenticated instructor.

- **Role required:** `INSTRUCTOR`

```bash
curl -s http://localhost:8080/api/instructor/courses \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN"
```

Expected: `200 OK`

#### GET /api/instructor/courses/{courseId}/enrolments

List all enrolments for a specific course owned by the instructor.

- **Role required:** `INSTRUCTOR`

```bash
curl -s http://localhost:8080/api/instructor/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/enrolments \
  -H "Authorization: Bearer $INSTRUCTOR_TOKEN"
```

Expected: `200 OK`

---

### Streaks

#### GET /api/learner/streaks

Get the authenticated learner's current learning streak.

- **Role required:** `LEARNER`

```bash
curl -s http://localhost:8080/api/learner/streaks \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200 OK`

---

### Admin — Courses

#### GET /api/admin/courses

List all courses (any status, any instructor).

- **Role required:** `ADMIN`

```bash
curl -s "http://localhost:8080/api/admin/courses?page=0&size=20" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected: `200 OK`

#### DELETE /api/admin/courses/{courseId}

Force-delete any course.

- **Role required:** `ADMIN`

```bash
curl -s -X DELETE http://localhost:8080/api/admin/courses/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected: `204 No Content`

---

### Admin — Enrolments

#### POST /api/admin/enrolments

Manually enrol any user in any course.

- **Role required:** `ADMIN`

```bash
curl -s -X POST http://localhost:8080/api/admin/enrolments \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "courseId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
  }'
```

Expected: `201 Created`

#### GET /api/admin/enrolments

List all enrolments across all courses.

- **Role required:** `ADMIN`

```bash
curl -s http://localhost:8080/api/admin/enrolments \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected: `200 OK`

#### DELETE /api/admin/enrolments/{enrolmentId}

Remove any enrolment.

- **Role required:** `ADMIN`

```bash
curl -s -X DELETE http://localhost:8080/api/admin/enrolments/11111111-2222-3333-4444-555555555555 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected: `204 No Content`

---

### Admin — Analytics

#### GET /api/admin/analytics

Retrieve platform-wide analytics (enrolment counts, completion rates, etc.).

- **Role required:** `ADMIN`

```bash
curl -s http://localhost:8080/api/admin/analytics \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected: `200 OK`

---

### Health

#### GET /actuator/health

```bash
curl -s http://localhost:8080/actuator/health
```

Expected: `200 OK` — `{"status":"UP"}`

---

## cert-service (port 8082)

For local testing without a running user-service, use:

```bash
-H "X-User-Id: 550e8400-e29b-41d4-a716-446655440000" \
-H "X-User-Roles: ROLE_LEARNER" \
-H "X-User-Email: alice@example.com"
```

Certificates are issued automatically by course-service when a learner completes a course. The cert-service exposes retrieval and download endpoints.

---

### Certificates

#### GET /api/learner/certificates

List all certificates earned by the authenticated learner.

- **Role required:** `LEARNER`

```bash
curl -s http://localhost:8082/api/learner/certificates \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200 OK` — array of certificate objects including `certificateUuid`, `courseId`, `issuedAt`.

#### GET /api/certificates/{uuid}/download

Redirect to a presigned S3 URL to download the certificate PDF. The `uuid` is the `certificateUuid` from the list response.

- **Role required:** `LEARNER` (must be the certificate owner)

```bash
# -L follows the redirect; omit -L to inspect the 302 Location header
curl -s -L http://localhost:8082/api/certificates/CERT-UUID-HERE/download \
  -H "Authorization: Bearer $TOKEN" \
  -o certificate.pdf
```

Expected: `302 Found` redirect to presigned S3 URL (or `404` if not found, `403` if not owner).

---

### Internal

#### GET /internal/certificates/{uuid}/exists

Internal endpoint used by course-service to check if a certificate has already been issued. Not authenticated; not intended for direct client use.

```bash
curl -s http://localhost:8082/internal/certificates/CERT-UUID-HERE/exists
```

Expected: `200 OK` — `{"exists": true}` or `{"exists": false}`

---

### Health

#### GET /actuator/health

```bash
curl -s http://localhost:8082/actuator/health
```

Expected: `200 OK` — `{"status":"UP"}`

---

## Swagger UI

Interactive API documentation is available when each service is running locally:

| Service        | Swagger UI URL                          |
|----------------|-----------------------------------------|
| user-service   | http://localhost:8081/swagger-ui.html   |
| course-service | http://localhost:8080/swagger-ui.html   |
| cert-service   | http://localhost:8082/swagger-ui.html   |

You can authorize in Swagger UI by clicking the **Authorize** button and entering `Bearer <your-token>`.
