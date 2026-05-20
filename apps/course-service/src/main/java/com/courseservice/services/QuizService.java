package com.courseservice.services;

import com.courseservice.dto.request.CreateQuizRequest;
import com.courseservice.dto.request.ReorderQuizzesRequest;
import com.courseservice.dto.request.UpdateQuizRequest;
import com.courseservice.dto.request.UpsertQuestionsRequest;
import com.courseservice.dto.response.QuizDetailResponse;
import com.courseservice.dto.response.QuizPlayerResponse;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.*;
import com.courseservice.models.Module;
import com.courseservice.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final CourseService courseService;
    private final ModuleRepository moduleRepository;
    private final QuizRepository quizRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final ModuleUnlockRepository moduleUnlockRepository;

    @Transactional
    public QuizDetailResponse create(UUID courseId, UUID moduleId, CreateQuizRequest req, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        Module module = loadModuleInCourse(moduleId, courseId);

        Quiz quiz = new Quiz();
        quiz.setModule(module);
        quiz.setTitle(req.title());
        quiz.setDescription(req.description());
        quiz.setOrderIndex(req.orderIndex());
        if (req.passingScore() != null) quiz.setPassingScore(req.passingScore());

        return QuizDetailResponse.from(quizRepository.save(quiz));
    }

    @Transactional
    public QuizDetailResponse update(UUID courseId, UUID moduleId, UUID quizId,
                                     UpdateQuizRequest req, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        loadModuleInCourse(moduleId, courseId);
        Quiz quiz = loadQuizInModule(quizId, moduleId);

        if (req.title() != null)        quiz.setTitle(req.title());
        if (req.description() != null)  quiz.setDescription(req.description());
        if (req.passingScore() != null) quiz.setPassingScore(req.passingScore());
        if (req.orderIndex() != null)   quiz.setOrderIndex(req.orderIndex());

        return QuizDetailResponse.from(quizRepository.save(quiz));
    }

    @Transactional
    public void reorder(UUID courseId, UUID moduleId, ReorderQuizzesRequest req, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        loadModuleInCourse(moduleId, courseId);
        quizRepository.shiftOrderIndicesUp(moduleId);
        for (var item : req.quizzes()) {
            quizRepository.updateOrderIndex(item.id(), item.orderIndex());
        }
    }

    @Transactional
    public void delete(UUID courseId, UUID moduleId, UUID quizId, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        loadModuleInCourse(moduleId, courseId);
        Quiz quiz = loadQuizInModule(quizId, moduleId);
        quizRepository.delete(quiz);
    }

    @Transactional(readOnly = true)
    public QuizDetailResponse getForInstructor(UUID courseId, UUID moduleId, UUID quizId, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        loadModuleInCourse(moduleId, courseId);
        Quiz quiz = loadQuizInModule(quizId, moduleId);
        return QuizDetailResponse.from(quiz);
    }

    @Transactional
    public QuizDetailResponse upsertQuestions(UUID courseId, UUID moduleId, UUID quizId,
                                              UpsertQuestionsRequest req, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        loadModuleInCourse(moduleId, courseId);
        Quiz quiz = loadQuizInModule(quizId, moduleId);

        quiz.getQuestions().clear();

        List<QuizQuestion> newQuestions = new ArrayList<>();
        for (int qi = 0; qi < req.questions().size(); qi++) {
            UpsertQuestionsRequest.QuestionDto qDto = req.questions().get(qi);
            QuizQuestion question = new QuizQuestion();
            question.setQuiz(quiz);
            question.setQuestionText(qDto.questionText());
            question.setQuestionType(qDto.questionType());
            question.setOrderIndex(qi);

            List<QuizOption> options = new ArrayList<>();
            for (int oi = 0; oi < qDto.options().size(); oi++) {
                UpsertQuestionsRequest.OptionDto oDto = qDto.options().get(oi);
                QuizOption option = new QuizOption();
                option.setQuestion(question);
                option.setOptionText(oDto.optionText());
                option.setCorrect(oDto.isCorrect());
                option.setOrderIndex(oi);
                options.add(option);
            }
            question.setOptions(options);
            newQuestions.add(question);
        }
        quiz.getQuestions().addAll(newQuestions);

        return QuizDetailResponse.from(quizRepository.save(quiz));
    }

    @Transactional(readOnly = true)
    public QuizPlayerResponse getForPlayer(UUID quizId, UUID userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

        UUID courseId = quiz.getModule().getCourse().getId();
        Enrolment enrolment = enrolmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("No active enrolment found for this course."));

        moduleUnlockRepository.findByEnrolmentIdAndModuleId(enrolment.getId(), quiz.getModule().getId())
                .orElseThrow(() -> new ResourceNotFoundException("This module is not yet unlocked."));

        return QuizPlayerResponse.from(quiz);
    }

    private com.courseservice.models.Module loadModuleInCourse(UUID moduleId, UUID courseId) {
        com.courseservice.models.Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + moduleId));
        if (!module.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Module " + moduleId + " does not belong to course " + courseId);
        }
        return module;
    }

    private Quiz loadQuizInModule(UUID quizId, UUID moduleId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
        if (!quiz.getModule().getId().equals(moduleId)) {
            throw new ResourceNotFoundException("Quiz " + quizId + " does not belong to module " + moduleId);
        }
        return quiz;
    }
}
