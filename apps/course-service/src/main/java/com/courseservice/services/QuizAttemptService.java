package com.courseservice.services;

import com.courseservice.dto.request.SubmitAttemptRequest;
import com.courseservice.dto.response.AttemptResultResponse;
import com.courseservice.exception.ModuleLockedForUserException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.*;
import com.courseservice.models.Module;
import com.courseservice.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizAttemptService {

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final ModuleUnlockRepository moduleUnlockRepository;
    private final ModuleProgressChecker moduleProgressChecker;

    @Caching(evict = {
            @CacheEvict(cacheNames = "analytics:instructor", allEntries = true),
            @CacheEvict(cacheNames = "analytics:admin",      key = "'platform'")
    })
    @Transactional
    public AttemptResultResponse submit(UUID quizId, SubmitAttemptRequest req, UUID userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

        Module module = quiz.getModule();
        UUID courseId = module.getCourse().getId();

        Enrolment enrolment = enrolmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("No active enrolment found for this course."));

        moduleUnlockRepository.findByEnrolmentIdAndModuleId(enrolment.getId(), module.getId())
                .orElseThrow(() -> new ModuleLockedForUserException("This module is not yet unlocked."));

        List<QuizQuestion> questions = quiz.getQuestions();
        Map<UUID, UUID> answers = req.answers();

        int correct = 0;
        List<AttemptResultResponse.QuestionResultDto> resultDetails = new ArrayList<>();

        for (QuizQuestion question : questions) {
            UUID selectedOptionId = answers.get(question.getId());
            UUID correctOptionId = question.getOptions().stream()
                    .filter(QuizOption::isCorrect)
                    .map(QuizOption::getId)
                    .findFirst()
                    .orElse(null);

            boolean isCorrect = selectedOptionId != null && selectedOptionId.equals(correctOptionId);
            if (isCorrect) correct++;

            resultDetails.add(new AttemptResultResponse.QuestionResultDto(
                    question.getId(), selectedOptionId, correctOptionId, isCorrect
            ));
        }

        int total = questions.size();
        int score = total == 0 ? 0 : (int) Math.round(100.0 * correct / total);
        boolean passed = score >= quiz.getPassingScore();

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setUserId(userId);
        attempt.setScore(score);
        attempt.setPassed(passed);
        quizAttemptRepository.save(attempt);

        ModuleProgressChecker.ModuleProgressResult progressResult =
                passed ? moduleProgressChecker.tryComplete(module, enrolment)
                       : ModuleProgressChecker.ModuleProgressResult.notDone();

        return new AttemptResultResponse(
                attempt.getId(), score, passed, quiz.getPassingScore(),
                progressResult.nextModuleId(), progressResult.courseCompleted(),
                resultDetails
        );
    }

    @Transactional(readOnly = true)
    public Optional<AttemptResultResponse> getBestAttempt(UUID quizId, UUID userId) {
        return quizAttemptRepository.findAllByQuizIdAndUserIdOrderByScoreDesc(quizId, userId)
                .stream().findFirst()
                .map(attempt -> new AttemptResultResponse(
                        attempt.getId(), attempt.getScore(), attempt.isPassed(),
                        attempt.getQuiz().getPassingScore(), null, false, List.of()
                ));
    }
}
