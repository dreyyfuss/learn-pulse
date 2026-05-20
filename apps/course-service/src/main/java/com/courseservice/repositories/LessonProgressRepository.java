package com.courseservice.repositories;

import com.courseservice.models.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

    Optional<LessonProgress> findByUserIdAndLessonId(UUID userId, UUID lessonId);

    List<LessonProgress> findByUserIdAndLessonIdIn(UUID userId, List<UUID> lessonIds);

    @Query("SELECT COUNT(lp) FROM LessonProgress lp WHERE lp.userId = :userId AND lp.lesson.module.id = :moduleId")
    long countByUserIdAndModuleId(@Param("userId") UUID userId, @Param("moduleId") UUID moduleId);

    @Query("SELECT COUNT(lp) FROM LessonProgress lp WHERE lp.userId = :userId AND lp.lesson.module.course.id = :courseId")
    long countCompletedByUserIdAndCourseId(@Param("userId") UUID userId, @Param("courseId") UUID courseId);

    @Modifying
    @Query("DELETE FROM LessonProgress lp WHERE lp.userId = :userId AND lp.lesson.module.course.id = :courseId")
    void deleteByUserIdAndCourseId(@Param("userId") UUID userId, @Param("courseId") UUID courseId);
}
