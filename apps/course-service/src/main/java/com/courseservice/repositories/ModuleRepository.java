package com.courseservice.repositories;

import com.courseservice.models.Module;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModuleRepository extends JpaRepository<Module, Long> {
    List<Module> findAllByCourse_IdOrderByOrderIndexAsc(Long courseId);
    long countByCourse_Id(Long courseId);
}
