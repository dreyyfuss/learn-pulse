package com.courseservice.services;

import com.courseservice.enums.ContentType;
import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.enums.JobStatus;
import com.courseservice.enums.QuestionType;
import com.courseservice.events.dto.CourseGenerationCompletedEvent;
import com.courseservice.events.dto.CourseGenerationCompletedEvent.GeneratedLesson;
import com.courseservice.events.dto.CourseGenerationCompletedEvent.GeneratedModule;
import com.courseservice.events.dto.CourseGenerationCompletedEvent.GeneratedOption;
import com.courseservice.events.dto.CourseGenerationCompletedEvent.GeneratedQuestion;
import com.courseservice.events.dto.CourseGenerationCompletedEvent.GeneratedQuiz;
import com.courseservice.events.dto.CourseGenerationFailedEvent;
import com.courseservice.events.producers.CourseGenerationProducer;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.dto.response.GenerationJobResponse;
import com.courseservice.models.Course;
import com.courseservice.models.CourseGenerationJob;
import com.courseservice.models.Lesson;
import com.courseservice.models.Module;
import com.courseservice.models.Quiz;
import com.courseservice.models.QuizOption;
import com.courseservice.models.QuizQuestion;
import com.courseservice.repositories.CourseGenerationJobRepository;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseGenerationService {

    private final CourseGenerationJobRepository jobRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final StorageService storageService;
    private final CourseGenerationProducer generationProducer;

    @Transactional
    public GenerationJobResponse initiate(String prompt, UUID instructorId) {
        CourseGenerationJob job = new CourseGenerationJob();
        job.setInstructorId(instructorId);
        job.setPrompt(prompt);
        jobRepository.save(job);

        generationProducer.requestGeneration(job);

        log.info("Course generation job created jobId={} instructorId={}", job.getId(), instructorId);
        return toResponse(job);
    }

    public GenerationJobResponse getJob(UUID jobId, UUID instructorId) {
        CourseGenerationJob job = jobRepository.findByIdAndInstructorId(jobId, instructorId)
                .orElseThrow(() -> new ResourceNotFoundException("Generation job not found"));
        return toResponse(job);
    }

    @Transactional
    public void handleCompleted(CourseGenerationCompletedEvent event) {
        UUID jobId = UUID.fromString(event.jobId());
        CourseGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Generation job not found: " + jobId));

        Course course = buildCourse(event);
        courseRepository.save(course);

        List<Lesson> lessonsWithContent = course.getModules().stream()
                .flatMap(m -> m.getLessons().stream())
                .filter(l -> l.getGeneratedContent() != null && !l.getGeneratedContent().isBlank())
                .toList();
        for (Lesson lesson : lessonsWithContent) {
            String key = "lessons/" + lesson.getId() + "/content.md";
            storageService.putObject(key, lesson.getGeneratedContent().getBytes(StandardCharsets.UTF_8), "text/markdown");
            lesson.setContentKey(key);
        }
        if (!lessonsWithContent.isEmpty()) {
            lessonRepository.saveAll(lessonsWithContent);
            log.info("Uploaded generated content to S3 for {} lessons courseId={}", lessonsWithContent.size(), course.getId());
        }

        job.setStatus(JobStatus.COMPLETED);
        job.setCourseId(course.getId());
        jobRepository.save(job);

        log.info("Course generation completed jobId={} courseId={}", jobId, course.getId());
    }

    @Transactional
    public void handleFailed(CourseGenerationFailedEvent event) {
        UUID jobId = UUID.fromString(event.jobId());
        CourseGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Generation job not found: " + jobId));

        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage(event.reason());
        jobRepository.save(job);

        log.warn("Course generation failed jobId={} reason={}", jobId, event.reason());
    }

    private Course buildCourse(CourseGenerationCompletedEvent event) {
        var gen = event.course();

        Course course = new Course();
        course.setInstructorId(UUID.fromString(event.instructorId()));
        course.setTitle(gen.title());
        course.setDescription(gen.description());
        course.setCategory(gen.category());
        course.setStatus(CourseStatus.DRAFT);
        course.setVisibility(CourseVisibility.PUBLIC);

        List<Module> modules = new ArrayList<>();
        for (GeneratedModule gm : gen.modules()) {
            Module module = new Module();
            module.setCourse(course);
            module.setTitle(gm.title());
            module.setDescription(gm.description());
            module.setOrderIndex(gm.orderIndex());

            var lessons = new LinkedHashSet<Lesson>();
            List<Quiz> quizzes = new ArrayList<>();

            for (GeneratedLesson gl : gm.lessons()) {
                Lesson lesson = new Lesson();
                lesson.setModule(module);
                lesson.setTitle(gl.title());
                lesson.setDescription(gl.description());
                lesson.setOrderIndex(gl.orderIndex());
                lesson.setContentType(ContentType.ARTICLE);
                lesson.setGeneratedContent(gl.content());
                lessons.add(lesson);

                if (gl.quiz() != null) {
                    quizzes.add(buildQuiz(module, gl.quiz(), gl.orderIndex()));
                }
            }

            module.setLessons(lessons);
            module.setQuizzes(quizzes);
            modules.add(module);
        }

        course.setModules(modules);
        return course;
    }

    private Quiz buildQuiz(Module module, GeneratedQuiz gq, int lessonOrderIndex) {
        Quiz quiz = new Quiz();
        quiz.setModule(module);
        quiz.setTitle(gq.title());
        quiz.setPassingScore(gq.passingScore());
        quiz.setOrderIndex(lessonOrderIndex);

        List<QuizQuestion> questions = new ArrayList<>();
        for (GeneratedQuestion gqn : gq.questions()) {
            QuizQuestion question = new QuizQuestion();
            question.setQuiz(quiz);
            question.setQuestionText(gqn.questionText());
            question.setQuestionType(QuestionType.valueOf(gqn.questionType()));
            question.setOrderIndex(gqn.orderIndex());

            List<QuizOption> options = new ArrayList<>();
            for (GeneratedOption go : gqn.options()) {
                QuizOption option = new QuizOption();
                option.setQuestion(question);
                option.setOptionText(go.optionText());
                option.setCorrect(go.isCorrect());
                option.setOrderIndex(go.orderIndex());
                options.add(option);
            }
            question.setOptions(options);
            questions.add(question);
        }
        quiz.setQuestions(questions);
        return quiz;
    }

    private GenerationJobResponse toResponse(CourseGenerationJob job) {
        return new GenerationJobResponse(job.getId(), job.getStatus(), job.getErrorMessage(), job.getCourseId());
    }
}
