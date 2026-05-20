package com.courseservice.repositories;

import com.courseservice.models.QuizOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizOptionRepository extends JpaRepository<QuizOption, UUID> {

    List<QuizOption> findByQuestionIdIn(List<UUID> questionIds);
}
