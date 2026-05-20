package com.courseservice.services;

import com.courseservice.dto.request.SubmitAttemptRequest;
import com.courseservice.dto.response.AttemptResultResponse;
import com.courseservice.exception.ModuleLockedForUserException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.*;
import com.courseservice.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizAttemptServiceTest {

    @Mock QuizRepository quizRepository;
    @Mock QuizAttemptRepository quizAttemptRepository;
    @Mock EnrolmentRepository enrolmentRepository;
    @Mock ModuleUnlockRepository moduleUnlockRepository;
    @Mock ModuleProgressChecker moduleProgressChecker;

    @InjectMocks QuizAttemptService quizAttemptService;

    private static final UUID USER_ID      = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COURSE_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MODULE_ID    = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID QUIZ_ID      = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ENROLMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID MODULE2_ID   = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID ATTEMPT_ID   = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Test
    void submit_allCorrect_score100_passes() {
        QuizSetup setup = buildQuiz(70, 2);
        Map<UUID, UUID> answers = new HashMap<>(setup.correctOptIds());
        stubHappyPath(setup.quiz());
        when(moduleProgressChecker.tryComplete(any(), any()))
                .thenReturn(new ModuleProgressChecker.ModuleProgressResult(null, false));

        AttemptResultResponse result = quizAttemptService.submit(
                QUIZ_ID, new SubmitAttemptRequest(answers), USER_ID);

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.passed()).isTrue();
        verify(quizAttemptRepository).save(any(QuizAttempt.class));
        verify(moduleProgressChecker).tryComplete(any(), any());
    }

    @Test
    void submit_allWrong_score0_fails() {
        QuizSetup setup = buildQuiz(70, 2);
        Map<UUID, UUID> answers = new HashMap<>(setup.wrongOptIds());
        stubHappyPath(setup.quiz());

        AttemptResultResponse result = quizAttemptService.submit(
                QUIZ_ID, new SubmitAttemptRequest(answers), USER_ID);

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.passed()).isFalse();
        verify(quizAttemptRepository).save(any(QuizAttempt.class));
        verify(moduleProgressChecker, never()).tryComplete(any(), any());
    }

    @Test
    void submit_belowThreshold_fails() {
        QuizSetup setup = buildQuiz(70, 2);
        List<UUID> qIds = setup.questionIds();
        Map<UUID, UUID> answers = new HashMap<>();
        answers.put(qIds.get(0), setup.correctOptIds().get(qIds.get(0)));
        answers.put(qIds.get(1), setup.wrongOptIds().get(qIds.get(1)));
        stubHappyPath(setup.quiz());

        AttemptResultResponse result = quizAttemptService.submit(
                QUIZ_ID, new SubmitAttemptRequest(answers), USER_ID);

        assertThat(result.score()).isEqualTo(50);
        assertThat(result.passed()).isFalse();
        verify(moduleProgressChecker, never()).tryComplete(any(), any());
    }

    @Test
    void submit_moduleNotUnlocked_throwsModuleLocked() {
        QuizSetup setup = buildQuiz(70, 1);
        Enrolment enrolment = buildEnrolment();
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(setup.quiz()));
        when(enrolmentRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.findByEnrolmentIdAndModuleId(ENROLMENT_ID, MODULE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizAttemptService.submit(
                QUIZ_ID, new SubmitAttemptRequest(Map.of()), USER_ID))
                .isInstanceOf(ModuleLockedForUserException.class);
    }

    @Test
    void submit_noEnrolment_throwsNotFound() {
        QuizSetup setup = buildQuiz(70, 1);
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(setup.quiz()));
        when(enrolmentRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizAttemptService.submit(
                QUIZ_ID, new SubmitAttemptRequest(Map.of()), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submit_perQuestionFeedback_correctAndWrong() {
        QuizSetup setup = buildQuiz(50, 2);
        List<UUID> qIds = setup.questionIds();
        UUID q0 = qIds.get(0), q1 = qIds.get(1);
        Map<UUID, UUID> answers = new HashMap<>();
        answers.put(q0, setup.correctOptIds().get(q0));
        answers.put(q1, setup.wrongOptIds().get(q1));
        stubHappyPath(setup.quiz());
        when(moduleProgressChecker.tryComplete(any(), any()))
                .thenReturn(new ModuleProgressChecker.ModuleProgressResult(null, false));

        AttemptResultResponse result = quizAttemptService.submit(
                QUIZ_ID, new SubmitAttemptRequest(answers), USER_ID);

        assertThat(result.questions()).hasSize(2);
        AttemptResultResponse.QuestionResultDto q0Result = result.questions().stream()
                .filter(q -> q.questionId().equals(q0)).findFirst().orElseThrow();
        AttemptResultResponse.QuestionResultDto q1Result = result.questions().stream()
                .filter(q -> q.questionId().equals(q1)).findFirst().orElseThrow();

        assertThat(q0Result.correct()).isTrue();
        assertThat(q0Result.correctOptionId()).isEqualTo(setup.correctOptIds().get(q0));
        assertThat(q1Result.correct()).isFalse();
        assertThat(q1Result.correctOptionId()).isEqualTo(setup.correctOptIds().get(q1));
    }

    @Test
    void submit_checkerReturnsNextModule_propagatedInResponse() {
        QuizSetup setup = buildQuiz(70, 1);
        Map<UUID, UUID> answers = new HashMap<>(setup.correctOptIds());
        stubHappyPath(setup.quiz());
        when(moduleProgressChecker.tryComplete(any(), any()))
                .thenReturn(new ModuleProgressChecker.ModuleProgressResult(MODULE2_ID, false));

        AttemptResultResponse result = quizAttemptService.submit(
                QUIZ_ID, new SubmitAttemptRequest(answers), USER_ID);

        assertThat(result.passed()).isTrue();
        assertThat(result.nextModuleId()).isEqualTo(MODULE2_ID);
        assertThat(result.courseCompleted()).isFalse();
    }

    @Test
    void getBestAttempt_hasAttempts_returnsBestScore() {
        Quiz quiz = new Quiz();
        quiz.setPassingScore(70);
        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(ATTEMPT_ID);
        attempt.setScore(85);
        attempt.setPassed(true);
        attempt.setQuiz(quiz);
        when(quizAttemptRepository.findAllByQuizIdAndUserIdOrderByScoreDesc(QUIZ_ID, USER_ID))
                .thenReturn(List.of(attempt));

        Optional<AttemptResultResponse> result = quizAttemptService.getBestAttempt(QUIZ_ID, USER_ID);

        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(85);
        assertThat(result.get().passed()).isTrue();
        assertThat(result.get().passingScore()).isEqualTo(70);
    }

    @Test
    void getBestAttempt_noAttempts_returnsEmpty() {
        when(quizAttemptRepository.findAllByQuizIdAndUserIdOrderByScoreDesc(QUIZ_ID, USER_ID))
                .thenReturn(List.of());

        Optional<AttemptResultResponse> result = quizAttemptService.getBestAttempt(QUIZ_ID, USER_ID);

        assertThat(result).isEmpty();
    }

    // --- helpers ---

    private record QuizSetup(
            Quiz quiz,
            List<UUID> questionIds,
            Map<UUID, UUID> correctOptIds,
            Map<UUID, UUID> wrongOptIds
    ) {}

    private QuizSetup buildQuiz(int passingScore, int numQuestions) {
        Course course = new Course();
        course.setId(COURSE_ID);
        com.courseservice.models.Module module = new com.courseservice.models.Module();
        module.setId(MODULE_ID);
        module.setCourse(course);

        Quiz quiz = new Quiz();
        quiz.setId(QUIZ_ID);
        quiz.setModule(module);
        quiz.setPassingScore(passingScore);

        List<UUID> questionIds = new ArrayList<>();
        Map<UUID, UUID> correctOptIds = new LinkedHashMap<>();
        Map<UUID, UUID> wrongOptIds = new LinkedHashMap<>();
        List<QuizQuestion> questions = new ArrayList<>();

        for (int i = 0; i < numQuestions; i++) {
            UUID qId = UUID.randomUUID();
            UUID correctId = UUID.randomUUID();
            UUID wrongId = UUID.randomUUID();

            QuizOption correctOpt = new QuizOption();
            correctOpt.setId(correctId);
            correctOpt.setCorrect(true);
            correctOpt.setOrderIndex(0);

            QuizOption wrongOpt = new QuizOption();
            wrongOpt.setId(wrongId);
            wrongOpt.setCorrect(false);
            wrongOpt.setOrderIndex(1);

            QuizQuestion q = new QuizQuestion();
            q.setId(qId);
            q.setQuiz(quiz);
            q.setOptions(List.of(correctOpt, wrongOpt));

            questions.add(q);
            questionIds.add(qId);
            correctOptIds.put(qId, correctId);
            wrongOptIds.put(qId, wrongId);
        }
        quiz.setQuestions(questions);
        return new QuizSetup(quiz, questionIds, correctOptIds, wrongOptIds);
    }

    private Enrolment buildEnrolment() {
        Enrolment e = new Enrolment();
        e.setId(ENROLMENT_ID);
        e.setUserId(USER_ID);
        Course course = new Course();
        course.setId(COURSE_ID);
        e.setCourse(course);
        return e;
    }

    private void stubHappyPath(Quiz quiz) {
        Enrolment enrolment = buildEnrolment();
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(enrolmentRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.findByEnrolmentIdAndModuleId(ENROLMENT_ID, MODULE_ID))
                .thenReturn(Optional.of(new ModuleUnlock()));
        when(quizAttemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> {
            QuizAttempt a = inv.getArgument(0);
            a.setId(ATTEMPT_ID);
            return a;
        });
    }
}
