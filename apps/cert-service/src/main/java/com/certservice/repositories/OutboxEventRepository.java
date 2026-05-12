package com.certservice.repositories;

import com.certservice.enums.OutboxStatus;
import com.certservice.models.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
