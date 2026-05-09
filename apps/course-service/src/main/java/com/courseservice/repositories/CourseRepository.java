package com.courseservice.repositories;

import com.courseservice.models.Course;
import com.courseservice.models.CourseStatus;
import com.courseservice.models.CourseVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findAllByInstructorId(Long instructorId);
    List<Course> findAllByStatusAndVisibility(CourseStatus status, CourseVisibility visibility);
    boolean existsByEnrolmentCode(String enrolmentCode);
}
