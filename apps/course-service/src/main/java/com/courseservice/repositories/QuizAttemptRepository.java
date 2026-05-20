package com.courseservice.repositories;

import com.courseservice.models.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    List<QuizAttempt> findByQuizIdAndUserIdOrderBySubmittedAtDesc(UUID quizId, UUID userId);

    boolean existsByQuizIdAndUserIdAndPassedTrue(UUID quizId, UUID userId);

    @Query("""
            SELECT COUNT(DISTINCT qa.quiz.id)
            FROM QuizAttempt qa
            WHERE qa.userId = :userId
              AND qa.quiz.module.id = :moduleId
              AND qa.passed = true
            """)
    long countPassedDistinctByUserIdAndModuleId(@Param("userId") UUID userId,
                                                @Param("moduleId") UUID moduleId);

    @Query("""
            SELECT qa FROM QuizAttempt qa
            WHERE qa.quiz.id = :quizId
              AND qa.userId = :userId
            ORDER BY qa.score DESC, qa.submittedAt DESC
            """)
    List<QuizAttempt> findAllByQuizIdAndUserIdOrderByScoreDesc(@Param("quizId") UUID quizId,
                                                               @Param("userId") UUID userId);
}
