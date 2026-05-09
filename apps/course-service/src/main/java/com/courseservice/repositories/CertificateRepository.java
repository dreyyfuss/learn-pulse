package com.courseservice.repositories;

import com.courseservice.models.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, String> {
    Optional<Certificate> findByEnrolmentId(Long enrolmentId);
    boolean existsByEnrolmentId(Long enrolmentId);
}
