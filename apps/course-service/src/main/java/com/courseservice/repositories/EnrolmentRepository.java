package com.courseservice.repositories;

import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.models.Enrolment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrolmentRepository extends JpaRepository<Enrolment, UUID> {

    Optional<Enrolment> findByUserIdAndCourseId(UUID userId, UUID courseId);

    boolean existsByUserIdAndCourseId(UUID userId, UUID courseId);

    Page<Enrolment> findByUserId(UUID userId, Pageable pageable);

    List<Enrolment> findByCourseId(UUID courseId);

    long countByCourseId(UUID courseId);

    long countByCourseIdAndStatus(UUID courseId, EnrolmentStatus status);

    long countByStatus(EnrolmentStatus status);

    // Analytics: per-learner lesson-completion counts in one round-trip.
    // Uses idx_enrolments_course_status + uk_lesson_progress_user_lesson (via leftmost-prefix) +
    // idx_lessons_module + idx_modules_course — all present in V2/V3 migrations.
    @Query("""
            SELECT e.userId                                                        AS userId,
                   e.status                                                         AS status,
                   e.enrolledAt                                                     AS enrolledAt,
                   e.completedAt                                                    AS completedAt,
                   (SELECT COUNT(lp) FROM LessonProgress lp
                    WHERE lp.userId = e.userId
                    AND lp.lesson.module.course.id = :courseId)                    AS lessonsCompleted
            FROM Enrolment e
            WHERE e.course.id = :courseId
            ORDER BY e.enrolledAt DESC
            """)
    List<LearnerProgressProjection> findLearnerProgressByCourseId(@Param("courseId") UUID courseId);
}
