package com.courseservice.repositories;

import com.courseservice.models.IdempotencyLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyLogRepository extends JpaRepository<IdempotencyLog, String> {
}
