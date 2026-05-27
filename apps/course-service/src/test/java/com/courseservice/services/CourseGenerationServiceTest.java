package com.courseservice.services;

import com.courseservice.enums.JobStatus;
import com.courseservice.events.dto.CourseGenerationCompletedEvent;
import com.courseservice.events.dto.CourseGenerationCompletedEvent.GeneratedCourse;
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
import com.courseservice.repositories.CourseGenerationJobRepository;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.LessonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class CourseGenerationServiceTest {

    @Mock CourseGenerationJobRepository jobRepository;
    @Mock CourseRepository              courseRepository;
    @Mock LessonRepository              lessonRepository;
    @Mock StorageService                storageService;
    @Mock CourseGenerationProducer      generationProducer;

    @InjectMocks CourseGenerationService courseGenerationService;

    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID JOB_ID        = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private CourseGenerationJob savedJob;

    @BeforeEach
    void setUp() {
        savedJob = new CourseGenerationJob();
        savedJob.setId(JOB_ID);
        savedJob.setInstructorId(INSTRUCTOR_ID);
        savedJob.setPrompt("Build a Python course");
        savedJob.setStatus(JobStatus.PENDING);
    }

    // ── initiate ─────────────────────────────────────────────────────────────

    @Test
    void initiate_savesJobAndFiresProducer() {
        when(jobRepository.save(any(CourseGenerationJob.class))).thenAnswer(inv -> {
            CourseGenerationJob j = inv.getArgument(0);
            j.setId(JOB_ID);
            return j;
        });

        courseGenerationService.initiate("Build a Python course", INSTRUCTOR_ID);

        verify(jobRepository).save(any(CourseGenerationJob.class));
        verify(generationProducer).requestGeneration(any(CourseGenerationJob.class));
    }

    @Test
    void initiate_returnsJobIdAndPendingStatus() {
        // JPA sets the UUID on the entity as a side-effect of save() — replicate that here
        when(jobRepository.save(any(CourseGenerationJob.class))).thenAnswer(inv -> {
            CourseGenerationJob j = inv.getArgument(0);
            j.setId(JOB_ID);
            return j;
        });

        GenerationJobResponse response = courseGenerationService.initiate("Build a Python course", INSTRUCTOR_ID);

        assertThat(response.jobId()).isEqualTo(JOB_ID);
        assertThat(response.status()).isEqualTo(JobStatus.PENDING);
        assertThat(response.courseId()).isNull();
    }

    // ── getJob ────────────────────────────────────────────────────────────────

    @Test
    void getJob_foundForOwner_returnsResponse() {
        when(jobRepository.findByIdAndInstructorId(JOB_ID, INSTRUCTOR_ID))
                .thenReturn(Optional.of(savedJob));

        GenerationJobResponse response = courseGenerationService.getJob(JOB_ID, INSTRUCTOR_ID);

        assertThat(response.jobId()).isEqualTo(JOB_ID);
        assertThat(response.status()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void getJob_notFound_throwsResourceNotFound() {
        when(jobRepository.findByIdAndInstructorId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseGenerationService.getJob(JOB_ID, INSTRUCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── handleCompleted ───────────────────────────────────────────────────────

    @Test
    void handleCompleted_jobNotFound_throwsResourceNotFound() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseGenerationService.handleCompleted(makeCompletedEvent(1, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void handleCompleted_setsJobStatusToCompleted() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(savedJob));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            c.setId(COURSE_ID);
            return c;
        });

        courseGenerationService.handleCompleted(makeCompletedEvent(1, 1));

        assertThat(savedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(savedJob.getCourseId()).isEqualTo(COURSE_ID);
        verify(jobRepository, atLeastOnce()).save(savedJob);
    }

    @Test
    void handleCompleted_persistsCourseWithCorrectTitle() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(savedJob));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        courseGenerationService.handleCompleted(makeCompletedEvent(1, 1));

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Python Basics");
        assertThat(captor.getValue().getInstructorId()).isEqualTo(INSTRUCTOR_ID);
    }

    @Test
    void handleCompleted_buildsCorrectModuleAndLessonCount() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(savedJob));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        courseGenerationService.handleCompleted(makeCompletedEvent(2, 3));

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(captor.capture());
        Course saved = captor.getValue();
        assertThat(saved.getModules()).hasSize(2);
        saved.getModules().forEach(m -> assertThat(m.getLessons()).hasSize(3));
    }

    @Test
    void handleCompleted_uploadsContentToStorageForEachLesson() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(savedJob));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        courseGenerationService.handleCompleted(makeCompletedEvent(1, 2));

        verify(storageService, times(2)).putObject(anyString(), any(byte[].class), eq("text/markdown"));
    }

    @Test
    void handleCompleted_storageKeyContainsLessonsPrefix() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(savedJob));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        courseGenerationService.handleCompleted(makeCompletedEvent(1, 1));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).putObject(keyCaptor.capture(), any(), any());
        assertThat(keyCaptor.getValue()).startsWith("lessons/").endsWith("/content.md");
    }

    // ── handleFailed ──────────────────────────────────────────────────────────

    @Test
    void handleFailed_setsJobStatusToFailed() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(savedJob));

        courseGenerationService.handleFailed(makeFailedEvent("LLM timeout"));

        assertThat(savedJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(savedJob.getErrorMessage()).isEqualTo("LLM timeout");
        verify(jobRepository).save(savedJob);
    }

    @Test
    void handleFailed_jobNotFound_throwsResourceNotFound() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseGenerationService.handleFailed(makeFailedEvent("timeout")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void handleFailed_doesNotTouchCourseRepository() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(savedJob));

        courseGenerationService.handleFailed(makeFailedEvent("rate limit"));

        verifyNoInteractions(courseRepository);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CourseGenerationCompletedEvent makeCompletedEvent(int numModules, int lessonsPerModule) {
        List<GeneratedModule> modules = new java.util.ArrayList<>();
        for (int m = 1; m <= numModules; m++) {
            List<GeneratedLesson> lessons = new java.util.ArrayList<>();
            for (int l = 1; l <= lessonsPerModule; l++) {
                GeneratedQuiz quiz = new GeneratedQuiz("Quiz " + l, 70, List.of(
                        new GeneratedQuestion("What is Python?", "MCQ", 1, List.of(
                                new GeneratedOption("A language", true, 1),
                                new GeneratedOption("A snake", false, 2)
                        ))
                ));
                lessons.add(new GeneratedLesson("Lesson " + m + "." + l, "Desc", l,
                        "## Lesson Content\n\nSome content here.", quiz));
            }
            modules.add(new GeneratedModule("Module " + m, "Module desc", m, lessons));
        }

        GeneratedCourse course = new GeneratedCourse(
                "Python Basics", "Learn Python", "Programming", modules);

        return new CourseGenerationCompletedEvent(
                UUID.randomUUID().toString(),
                "course.generation.completed",
                1,
                "2026-01-01T00:00:00Z",
                JOB_ID.toString(),
                INSTRUCTOR_ID.toString(),
                course
        );
    }

    private CourseGenerationFailedEvent makeFailedEvent(String reason) {
        return new CourseGenerationFailedEvent(
                UUID.randomUUID().toString(),
                "course.generation.failed",
                1,
                "2026-01-01T00:00:00Z",
                JOB_ID.toString(),
                INSTRUCTOR_ID.toString(),
                reason
        );
    }
}
