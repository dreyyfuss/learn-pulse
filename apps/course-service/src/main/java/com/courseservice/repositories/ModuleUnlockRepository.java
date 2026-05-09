package com.courseservice.repositories;

import com.courseservice.models.ModuleUnlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModuleUnlockRepository extends JpaRepository<ModuleUnlock, Long> {
    boolean existsByEnrolment_IdAndModule_Id(Long enrolmentId, Long moduleId);
    List<ModuleUnlock> findAllByEnrolment_Id(Long enrolmentId);
}
