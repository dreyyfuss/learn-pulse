package com.courseservice.repositories;

import com.courseservice.models.LessonAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonAttachmentRepository extends JpaRepository<LessonAttachment, Long> {
    List<LessonAttachment> findAllByLesson_Id(Long lessonId);
}
