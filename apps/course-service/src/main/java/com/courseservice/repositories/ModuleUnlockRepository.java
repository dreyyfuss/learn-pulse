package com.courseservice.repositories;

import com.courseservice.models.ModuleUnlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModuleUnlockRepository extends JpaRepository<ModuleUnlock, UUID> {

    Optional<ModuleUnlock> findByEnrolmentIdAndModuleId(UUID enrolmentId, UUID moduleId);

    List<ModuleUnlock> findByEnrolmentId(UUID enrolmentId);
}
