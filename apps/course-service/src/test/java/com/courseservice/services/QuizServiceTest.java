package com.courseservice.services;

import com.courseservice.dto.request.CreateQuizRequest;
import com.courseservice.dto.request.ReorderQuizzesRequest;
import com.courseservice.dto.request.UpdateQuizRequest;
import com.courseservice.dto.request.UpsertQuestionsRequest;
import com.courseservice.dto.response.QuizDetailResponse;
import com.courseservice.dto.response.QuizPlayerResponse;
import com.courseservice.enums.QuestionType;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.*;
import com.courseservice.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock CourseService courseService;
    @Mock ModuleRepository moduleRepository;
    @Mock QuizRepository quizRepository;
    @Mock EnrolmentRepository enrolmentRepository;
    @Mock ModuleUnlockRepository moduleUnlockRepository;

    @InjectMocks QuizService quizService;

    private static final UUID INSTRUCTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COURSE_ID      = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MODULE_ID      = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID QUIZ_ID        = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OTHER_MODULE   = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID ENROLMENT_ID   = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID USER_ID        = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Test
    void create_validRequest_savedAndReturned() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE_ID, course);
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(course);
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> {
            Quiz q = inv.getArgument(0);
            q.setId(QUIZ_ID);
            return q;
        });

        CreateQuizRequest req = new CreateQuizRequest("Quiz 1", null, 70, 0);
        QuizDetailResponse response = quizService.create(COURSE_ID, MODULE_ID, req, INSTRUCTOR_ID);

        assertThat(response.id()).isEqualTo(QUIZ_ID);
        assertThat(response.title()).isEqualTo("Quiz 1");
        assertThat(response.passingScore()).isEqualTo(70);
        verify(quizRepository).save(any(Quiz.class));
    }

    @Test
    void create_notCourseOwner_throwsNotOwner() {
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID))
                .thenThrow(new NotOwnerException("You do not own this course."));

        assertThatThrownBy(() -> quizService.create(COURSE_ID, MODULE_ID,
                new CreateQuizRequest("Quiz", null, null, 0), INSTRUCTOR_ID))
                .isInstanceOf(NotOwnerException.class);
    }

    @Test
    void update_partialFields_onlyChangedFieldsUpdated() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE_ID, course);
        Quiz quiz = buildQuiz(QUIZ_ID, module, "Old Title", 70);
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(course);
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateQuizRequest req = new UpdateQuizRequest("New Title", null, null, null);
        QuizDetailResponse response = quizService.update(COURSE_ID, MODULE_ID, QUIZ_ID, req, INSTRUCTOR_ID);

        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.passingScore()).isEqualTo(70);
    }

    @Test
    void reorder_validRequest_shiftsAndUpdatesEachQuiz() {
        UUID QUIZ_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000008");
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE_ID, course);
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(course);
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));

        ReorderQuizzesRequest req = new ReorderQuizzesRequest(List.of(
                new ReorderQuizzesRequest.QuizOrderItem(QUIZ_ID, 0),
                new ReorderQuizzesRequest.QuizOrderItem(QUIZ_ID_2, 1)
        ));
        quizService.reorder(COURSE_ID, MODULE_ID, req, INSTRUCTOR_ID);

        verify(quizRepository).shiftOrderIndicesUp(MODULE_ID);
        verify(quizRepository).updateOrderIndex(QUIZ_ID, 0);
        verify(quizRepository).updateOrderIndex(QUIZ_ID_2, 1);
    }

    @Test
    void delete_existingQuiz_deletesIt() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE_ID, course);
        Quiz quiz = buildQuiz(QUIZ_ID, module, "Quiz", 70);
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(course);
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));

        quizService.delete(COURSE_ID, MODULE_ID, QUIZ_ID, INSTRUCTOR_ID);

        verify(quizRepository).delete(quiz);
    }

    @Test
    void getForInstructor_validRequest_returnsDetailWithOptions() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE_ID, course);
        Quiz quiz = buildQuizWithQuestion(QUIZ_ID, module);
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(course);
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));

        QuizDetailResponse response = quizService.getForInstructor(COURSE_ID, MODULE_ID, QUIZ_ID, INSTRUCTOR_ID);

        assertThat(response.id()).isEqualTo(QUIZ_ID);
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).options()).hasSize(2);
    }

    @Test
    void getForInstructor_quizInDifferentModule_throwsNotFound() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE_ID, course);
        com.courseservice.models.Module otherModule = buildModule(OTHER_MODULE, course);
        Quiz quiz = buildQuiz(QUIZ_ID, otherModule, "Quiz", 70);
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(course);
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() ->
                quizService.getForInstructor(COURSE_ID, MODULE_ID, QUIZ_ID, INSTRUCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upsertQuestions_replacesAllQuestionsAndSaves() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE_ID, course);
        Quiz quiz = buildQuiz(QUIZ_ID, module, "Quiz", 70);
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(course);
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));

        UpsertQuestionsRequest req = new UpsertQuestionsRequest(List.of(
                new UpsertQuestionsRequest.QuestionDto("What is 2+2?", QuestionType.MCQ, List.of(
                        new UpsertQuestionsRequest.OptionDto("3", false),
                        new UpsertQuestionsRequest.OptionDto("4", true)
                ))
        ));
        QuizDetailResponse response = quizService.upsertQuestions(COURSE_ID, MODULE_ID, QUIZ_ID, req, INSTRUCTOR_ID);

        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).questionText()).isEqualTo("What is 2+2?");
        assertThat(response.questions().get(0).options()).hasSize(2);
        verify(quizRepository).save(quiz);
    }

    @Test
    void getForPlayer_notEnrolled_throwsNotFound() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE_ID, course);
        Quiz quiz = buildQuiz(QUIZ_ID, module, "Quiz", 70);
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(enrolmentRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.getForPlayer(QUIZ_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getForPlayer_moduleNotUnlocked_throwsNotFound() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE_ID, course);
        Quiz quiz = buildQuiz(QUIZ_ID, module, "Quiz", 70);
        Enrolment enrolment = buildEnrolment(course);
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(enrolmentRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.findByEnrolmentIdAndModuleId(ENROLMENT_ID, MODULE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.getForPlayer(QUIZ_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getForPlayer_validEnrolment_returnsPlayerResponseWithoutCorrectFlags() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE_ID, course);
        Quiz quiz = buildQuizWithQuestion(QUIZ_ID, module);
        Enrolment enrolment = buildEnrolment(course);
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(enrolmentRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.findByEnrolmentIdAndModuleId(ENROLMENT_ID, MODULE_ID))
                .thenReturn(Optional.of(new ModuleUnlock()));

        QuizPlayerResponse response = quizService.getForPlayer(QUIZ_ID, USER_ID);

        assertThat(response.id()).isEqualTo(QUIZ_ID);
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).options()).hasSize(2);
    }

    // --- helpers ---

    private Course buildCourse() {
        Course c = new Course();
        c.setId(COURSE_ID);
        c.setInstructorId(INSTRUCTOR_ID);
        return c;
    }

    private com.courseservice.models.Module buildModule(UUID id, Course course) {
        com.courseservice.models.Module m = new com.courseservice.models.Module();
        m.setId(id);
        m.setCourse(course);
        m.setTitle("Module");
        m.setOrderIndex(1);
        return m;
    }

    private Quiz buildQuiz(UUID id, com.courseservice.models.Module module, String title, int passingScore) {
        Quiz q = new Quiz();
        q.setId(id);
        q.setModule(module);
        q.setTitle(title);
        q.setPassingScore(passingScore);
        q.setOrderIndex(0);
        return q;
    }

    private Quiz buildQuizWithQuestion(UUID id, com.courseservice.models.Module module) {
        Quiz quiz = buildQuiz(id, module, "Quiz", 70);

        QuizOption correctOpt = new QuizOption();
        correctOpt.setId(UUID.randomUUID());
        correctOpt.setOptionText("True");
        correctOpt.setCorrect(true);
        correctOpt.setOrderIndex(0);

        QuizOption wrongOpt = new QuizOption();
        wrongOpt.setId(UUID.randomUUID());
        wrongOpt.setOptionText("False");
        wrongOpt.setCorrect(false);
        wrongOpt.setOrderIndex(1);

        QuizQuestion question = new QuizQuestion();
        question.setId(UUID.randomUUID());
        question.setQuiz(quiz);
        question.setQuestionText("Is the sky blue?");
        question.setQuestionType(QuestionType.TRUE_FALSE);
        question.setOrderIndex(0);
        question.setOptions(List.of(correctOpt, wrongOpt));

        quiz.setQuestions(List.of(question));
        return quiz;
    }

    private Enrolment buildEnrolment(Course course) {
        Enrolment e = new Enrolment();
        e.setId(ENROLMENT_ID);
        e.setUserId(USER_ID);
        e.setCourse(course);
        return e;
    }
}
