package com.courseservice.services;

import com.courseservice.dto.request.AttachmentConfirmRequest;
import com.courseservice.dto.request.AttachmentUploadUrlRequest;
import com.courseservice.dto.request.ContentConfirmRequest;
import com.courseservice.dto.request.ContentUploadUrlRequest;
import com.courseservice.dto.response.AttachmentDownloadUrlResponse;
import com.courseservice.dto.response.AttachmentResponse;
import com.courseservice.dto.response.ContentUploadUrlResponse;
import com.courseservice.dto.response.LessonContentResponse;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Course;
import com.courseservice.models.Enrolment;
import com.courseservice.models.Lesson;
import com.courseservice.models.LessonAttachment;
import com.courseservice.models.Module;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.EnrolmentRepository;
import com.courseservice.repositories.LessonAttachmentRepository;
import com.courseservice.repositories.LessonRepository;
import com.courseservice.repositories.ModuleRepository;
import com.courseservice.repositories.ModuleUnlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LessonContentService {

    private static final Duration UPLOAD_TTL   = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_TTL = Duration.ofHours(1);

    private static final Map<String, String> MIME_TO_EXT = Map.ofEntries(
            Map.entry("video/mp4",        ".mp4"),
            Map.entry("video/webm",       ".webm"),
            Map.entry("video/ogg",        ".ogv"),
            Map.entry("application/pdf",  ".pdf"),
            Map.entry("text/markdown",    ".md"),
            Map.entry("application/msword",
                    ".doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    ".docx"),
            Map.entry("application/zip",  ".zip")
    );

    private final CourseRepository          courseRepository;
    private final ModuleRepository          moduleRepository;
    private final LessonRepository          lessonRepository;
    private final LessonAttachmentRepository attachmentRepository;
    private final EnrolmentRepository       enrolmentRepository;
    private final ModuleUnlockRepository    moduleUnlockRepository;
    private final StorageService            storageService;

    // ── Content upload ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ContentUploadUrlResponse generateContentUploadUrl(
            UUID courseId, UUID moduleId, UUID lessonId,
            ContentUploadUrlRequest req, UUID instructorId) {

        Lesson lesson = loadVerifiedForInstructor(courseId, moduleId, lessonId, instructorId);
        String ext = MIME_TO_EXT.getOrDefault(req.mimeType(), ".bin");
        String key = "lessons/" + lesson.getId() + "/content" + ext;
        String url = storageService.presignUploadUrl(key, req.mimeType(), UPLOAD_TTL);
        return new ContentUploadUrlResponse(url, key);
    }

    @Transactional
    public void confirmContentUpload(
            UUID courseId, UUID moduleId, UUID lessonId,
            ContentConfirmRequest req, UUID instructorId) {

        Lesson lesson = loadVerifiedForInstructor(courseId, moduleId, lessonId, instructorId);
        String expectedPrefix = "lessons/" + lesson.getId() + "/";
        if (!req.objectKey().startsWith(expectedPrefix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Object key does not match lesson " + lessonId);
        }
        lesson.setContentKey(req.objectKey());
        lessonRepository.save(lesson);
    }

    // ── Content retrieval ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LessonContentResponse getLessonContent(
            UUID courseId, UUID moduleId, UUID lessonId, UUID userId) {

        Lesson lesson = loadVerifiedForViewer(courseId, moduleId, lessonId, userId);
        String presignedUrl = lesson.getContentKey() != null
                ? storageService.presignDownloadUrl(lesson.getContentKey(), DOWNLOAD_TTL)
                : null;
        return new LessonContentResponse(lesson.getContentType(), presignedUrl, lesson.getContentUrl());
    }

    // ── Content delete ────────────────────────────────────────────────────────

    @Transactional
    public void deleteContent(UUID courseId, UUID moduleId, UUID lessonId, UUID instructorId) {
        Lesson lesson = loadVerifiedForInstructor(courseId, moduleId, lessonId, instructorId);
        if (lesson.getContentKey() != null) {
            storageService.delete(lesson.getContentKey());
            lesson.setContentKey(null);
            lessonRepository.save(lesson);
        }
    }

    // ── Attachment upload ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ContentUploadUrlResponse generateAttachmentUploadUrl(
            UUID courseId, UUID moduleId, UUID lessonId,
            AttachmentUploadUrlRequest req, UUID instructorId) {

        Lesson lesson = loadVerifiedForInstructor(courseId, moduleId, lessonId, instructorId);
        String sanitized = req.fileName().replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "lessons/" + lesson.getId() + "/attachments/" + sanitized;
        String url = storageService.presignUploadUrl(key, req.mimeType(), UPLOAD_TTL);
        return new ContentUploadUrlResponse(url, key);
    }

    @Transactional
    public AttachmentResponse confirmAttachmentUpload(
            UUID courseId, UUID moduleId, UUID lessonId,
            AttachmentConfirmRequest req, UUID instructorId) {

        Lesson lesson = loadVerifiedForInstructor(courseId, moduleId, lessonId, instructorId);
        String expectedPrefix = "lessons/" + lesson.getId() + "/";
        if (!req.objectKey().startsWith(expectedPrefix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Object key does not match lesson " + lessonId);
        }
        LessonAttachment attachment = new LessonAttachment();
        attachment.setLesson(lesson);
        attachment.setFileName(req.fileName());
        attachment.setS3Key(req.objectKey());
        attachment.setMimeType(req.mimeType());
        return AttachmentResponse.from(attachmentRepository.save(attachment));
    }

    // ── Attachment download ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AttachmentDownloadUrlResponse getAttachmentDownloadUrl(
            UUID courseId, UUID moduleId, UUID lessonId,
            UUID attachmentId, UUID userId) {

        loadVerifiedForViewer(courseId, moduleId, lessonId, userId);
        LessonAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
        if (!attachment.getLesson().getId().equals(lessonId)) {
            throw new ResourceNotFoundException("Attachment does not belong to lesson " + lessonId);
        }
        String key = attachment.getS3Key() != null ? attachment.getS3Key() : attachment.getS3Url();
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment has no downloadable content");
        }
        // Legacy attachments store a full URL in s3Url; S3 keys start with "lessons/"
        String url = key.startsWith("lessons/")
                ? storageService.presignDownloadUrl(key, DOWNLOAD_TTL)
                : key;
        return new AttachmentDownloadUrlResponse(url);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Lesson loadVerifiedForInstructor(UUID courseId, UUID moduleId, UUID lessonId, UUID instructorId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));
        if (!course.getInstructorId().equals(instructorId)) {
            throw new NotOwnerException("You do not own course " + courseId);
        }
        return loadLessonInModule(lessonId, moduleId, courseId);
    }

    private Lesson loadVerifiedForViewer(UUID courseId, UUID moduleId, UUID lessonId, UUID userId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));

        // Instructors can always view their own course content
        if (course.getInstructorId().equals(userId)) {
            return loadLessonInModule(lessonId, moduleId, courseId);
        }

        // Learners need an active enrolment with the module unlocked
        Enrolment enrolment = enrolmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You are not enrolled in this course"));

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + moduleId));
        if (!module.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Module does not belong to course " + courseId);
        }

        boolean unlocked = moduleUnlockRepository
                .findByEnrolmentIdAndModuleId(enrolment.getId(), moduleId)
                .isPresent();
        if (!unlocked) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This module is not yet unlocked");
        }

        return loadLessonInModule(lessonId, moduleId, courseId);
    }

    private Lesson loadLessonInModule(UUID lessonId, UUID moduleId, UUID courseId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));
        if (!lesson.getModule().getId().equals(moduleId)) {
            throw new ResourceNotFoundException("Lesson does not belong to module " + moduleId);
        }
        if (!lesson.getModule().getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Module does not belong to course " + courseId);
        }
        return lesson;
    }
}
