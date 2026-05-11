package com.courseservice.repositories;

import com.courseservice.enums.OutboxStatus;
import com.courseservice.models.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status ORDER BY o.createdAt ASC LIMIT 20")
    List<OutboxEvent> findTop20ByStatusOrderByCreatedAtAsc(@Param("status") OutboxStatus status);
}
