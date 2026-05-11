package com.courseservice.repositories;

import com.courseservice.models.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ModuleRepository extends JpaRepository<Module, UUID> {

    List<Module> findByCourseIdOrderByOrderIndex(UUID courseId);

    @Modifying
    @Query("UPDATE Module m SET m.orderIndex = :orderIndex WHERE m.id = :id")
    void updateOrderIndex(@Param("id") UUID id, @Param("orderIndex") int orderIndex);
}
