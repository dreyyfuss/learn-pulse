package com.courseservice.repositories;

import com.courseservice.models.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    List<Quiz> findByModuleIdOrderByOrderIndex(UUID moduleId);

    long countByModuleId(UUID moduleId);

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.module.course.id = :courseId")
    long countByCourseId(@Param("courseId") UUID courseId);

    @Modifying
    @Query("UPDATE Quiz q SET q.orderIndex = q.orderIndex + 10000 WHERE q.module.id = :moduleId")
    void shiftOrderIndicesUp(@Param("moduleId") UUID moduleId);

    @Modifying
    @Query("UPDATE Quiz q SET q.orderIndex = :orderIndex WHERE q.id = :id")
    void updateOrderIndex(@Param("id") UUID id, @Param("orderIndex") int orderIndex);
}
