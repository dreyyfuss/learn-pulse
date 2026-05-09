package com.courseservice.repositories;

import com.courseservice.models.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
    Optional<LessonProgress> findByUser_IdAndLesson_Id(Long userId, Long lessonId);
    boolean existsByUser_IdAndLesson_Id(Long userId, Long lessonId);
    List<LessonProgress> findAllByUser_Id(Long userId);
    long countByUser_IdAndLesson_Module_Id(Long userId, Long moduleId);

    @Query("SELECT COUNT(lp) FROM LessonProgress lp WHERE lp.user.id = :userId AND lp.lesson.module.course.id = :courseId")
    long countCompletedByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    // Subquery avoids a multi-table DELETE which is not portable across JPA providers
    @Modifying
    @Query("DELETE FROM LessonProgress lp WHERE lp.user.id = :userId " +
           "AND lp.lesson.id IN (SELECT l.id FROM Lesson l WHERE l.module.course.id = :courseId)")
    void deleteByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
