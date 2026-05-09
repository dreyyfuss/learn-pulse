package com.certservice.repository;

import com.certservice.model.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, String> {
    Optional<Certificate> findByEnrolmentId(Long enrolmentId);

    List<Certificate> findAllByUserIdOrderByIssuedAtDesc(Long userId);
}
