package com.courseservice.repositories;

import com.courseservice.models.LessonAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonAttachmentRepository extends JpaRepository<LessonAttachment, UUID> {

    List<LessonAttachment> findByLessonId(UUID lessonId);
}
