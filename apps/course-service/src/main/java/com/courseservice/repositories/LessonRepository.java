package com.courseservice.repositories;

import com.courseservice.models.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findAllByModule_IdOrderByOrderIndexAsc(Long moduleId);
    long countByModule_Id(Long moduleId);

    @Query("SELECT COUNT(l) FROM Lesson l WHERE l.module.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);
}
