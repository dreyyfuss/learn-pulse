package com.certservice.repositories;

import com.certservice.models.IdempotencyLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyLogRepository extends JpaRepository<IdempotencyLog, String> {
    boolean existsByEventId(String eventId);
}
