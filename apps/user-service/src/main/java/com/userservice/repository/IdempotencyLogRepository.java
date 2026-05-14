package com.userservice.repository;

import com.userservice.domain.idempotency.IdempotencyLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyLogRepository extends JpaRepository<IdempotencyLog, String> {
    boolean existsByEventId(String eventId);
}
