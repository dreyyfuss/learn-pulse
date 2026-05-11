package com.courseservice.services;

import com.courseservice.dto.request.AttachmentRequest;
import com.courseservice.dto.request.CreateLessonRequest;
import com.courseservice.dto.request.ReorderLessonsRequest;
import com.courseservice.dto.request.UpdateLessonRequest;
import com.courseservice.dto.response.LessonDetailResponse;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Lesson;
import com.courseservice.models.LessonAttachment;
import com.courseservice.models.Module;
import com.courseservice.repositories.LessonRepository;
import com.courseservice.repositories.ModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LessonService {

    private final CourseService courseService;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;

    @Transactional
    public LessonDetailResponse create(UUID courseId, UUID moduleId, CreateLessonRequest req, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        Module module = loadModuleInCourse(moduleId, courseId);

        Lesson lesson = new Lesson();
        lesson.setModule(module);
        lesson.setTitle(req.title());
        lesson.setDescription(req.description());
        lesson.setContentType(req.contentType());
        lesson.setContentUrl(req.contentUrl());
        lesson.setOrderIndex(req.orderIndex());

        if (req.attachments() != null) {
            for (AttachmentRequest a : req.attachments()) {
                LessonAttachment attachment = new LessonAttachment();
                attachment.setLesson(lesson);
                attachment.setFileName(a.fileName());
                attachment.setS3Url(a.s3Url());
                attachment.setMimeType(a.mimeType());
                lesson.getAttachments().add(attachment);
            }
        }

        return LessonDetailResponse.from(lessonRepository.save(lesson));
    }

    @Transactional
    public LessonDetailResponse update(UUID courseId, UUID moduleId, UUID lessonId,
                                       UpdateLessonRequest req, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        loadModuleInCourse(moduleId, courseId);
        Lesson lesson = loadLessonInModule(lessonId, moduleId);

        if (req.title() != null)       lesson.setTitle(req.title());
        if (req.description() != null) lesson.setDescription(req.description());
        if (req.contentType() != null) lesson.setContentType(req.contentType());
        if (req.contentUrl() != null)  lesson.setContentUrl(req.contentUrl());
        if (req.orderIndex() != null)  lesson.setOrderIndex(req.orderIndex());

        return LessonDetailResponse.from(lessonRepository.save(lesson));
    }

    @Transactional
    public void reorder(UUID courseId, UUID moduleId, ReorderLessonsRequest req, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        loadModuleInCourse(moduleId, courseId);
        lessonRepository.shiftOrderIndicesUp(moduleId);
        for (var item : req.lessons()) {
            lessonRepository.updateOrderIndex(item.id(), item.orderIndex());
        }
    }

    @Transactional
    public void delete(UUID courseId, UUID moduleId, UUID lessonId, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        loadModuleInCourse(moduleId, courseId);
        Lesson lesson = loadLessonInModule(lessonId, moduleId);
        lessonRepository.delete(lesson);
    }

    private Module loadModuleInCourse(UUID moduleId, UUID courseId) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + moduleId));
        if (!module.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Module " + moduleId + " does not belong to course " + courseId);
        }
        return module;
    }

    private Lesson loadLessonInModule(UUID lessonId, UUID moduleId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));
        if (!lesson.getModule().getId().equals(moduleId)) {
            throw new ResourceNotFoundException("Lesson " + lessonId + " does not belong to module " + moduleId);
        }
        return lesson;
    }
}
