package com.courseservice.repositories;

import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.models.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    @Query("SELECT DISTINCT c FROM Course c LEFT JOIN FETCH c.modules WHERE c.id = :id")
    Optional<Course> findWithModulesAndLessonsById(@Param("id") UUID id);

    @Query("SELECT c FROM Course c WHERE c.status = 'PUBLISHED' " +
           "AND (:q IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:category IS NULL OR c.category = :category) " +
           "AND (:visibility IS NULL OR c.visibility = :visibility)")
    Page<Course> findPublishedCourses(
            @Param("q") String q,
            @Param("category") String category,
            @Param("visibility") CourseVisibility visibility,
            Pageable pageable);

    long countByStatus(CourseStatus status);

    Page<Course> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Course> findAllByInstructorId(UUID instructorId, Pageable pageable);
}
