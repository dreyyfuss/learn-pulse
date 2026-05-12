package com.certservice.repositories;

import com.certservice.models.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {
    List<Certificate> findByUserId(String userId);
    Optional<Certificate> findByCertificateUuid(String certificateUuid);
}
