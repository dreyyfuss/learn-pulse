package com.courseservice.repositories;

import com.courseservice.models.CourseGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseGenerationJobRepository extends JpaRepository<CourseGenerationJob, UUID> {
    Optional<CourseGenerationJob> findByIdAndInstructorId(UUID id, UUID instructorId);
}
