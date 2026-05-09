package com.courseservice.repositories;

import com.courseservice.models.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByModuleIdOrderByOrderIndex(UUID moduleId);

    @Query("SELECT COUNT(l) FROM Lesson l WHERE l.module.course.id = :courseId")
    long countByCourseId(@Param("courseId") UUID courseId);
}
