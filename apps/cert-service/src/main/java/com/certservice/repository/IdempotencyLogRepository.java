package com.certservice.repository;

import com.certservice.model.IdempotencyLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyLogRepository extends JpaRepository<IdempotencyLog, String> {
}
