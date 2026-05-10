package com.courseservice.repositories;

import com.courseservice.models.Enrolment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrolmentRepository extends JpaRepository<Enrolment, UUID> {

    Optional<Enrolment> findByUserIdAndCourseId(UUID userId, UUID courseId);

    boolean existsByUserIdAndCourseId(UUID userId, UUID courseId);

    Page<Enrolment> findByUserId(UUID userId, Pageable pageable);

    List<Enrolment> findByCourseId(UUID courseId);
}
