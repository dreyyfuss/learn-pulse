package com.courseservice.repositories;

import com.courseservice.models.Enrolment;
import com.courseservice.models.EnrolmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {
    Optional<Enrolment> findByUser_IdAndCourse_Id(Long userId, Long courseId);
    boolean existsByUser_IdAndCourse_Id(Long userId, Long courseId);
    List<Enrolment> findAllByUser_Id(Long userId);
    List<Enrolment> findAllByCourse_Id(Long courseId);
    List<Enrolment> findAllByCourse_IdAndStatus(Long courseId, EnrolmentStatus status);
}
