package com.courseservice.repository;

import com.courseservice.enums.ContentType;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.enums.LessonProgressStatus;
import com.courseservice.models.Course;
import com.courseservice.models.Enrolment;
import com.courseservice.models.Lesson;
import com.courseservice.models.LessonProgress;
import com.courseservice.models.ModuleUnlock;
import com.courseservice.repositories.EnrolmentRepository;
import com.courseservice.repositories.LessonProgressRepository;
import com.courseservice.repositories.ModuleUnlockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EnrolmentRepositoryTest {

    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Autowired
    private LessonProgressRepository lessonProgressRepository;

    @Autowired
    private ModuleUnlockRepository moduleUnlockRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void enrolmentSaveAndLoadRoundTrip() {
        Course course = persistCourse();

        Enrolment enrolment = new Enrolment();
        enrolment.setUserId(UUID.randomUUID());
        enrolment.setCourse(course);

        Enrolment saved = enrolmentRepository.save(enrolment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEnrolledAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(EnrolmentStatus.ACTIVE);
        assertThat(saved.getStartedAt()).isNull();
        assertThat(saved.getCompletedAt()).isNull();
    }

    @Test
    void findByUserIdAndCourseId_returnsEnrolment() {
        Course course = persistCourse();
        UUID userId = UUID.randomUUID();

        Enrolment enrolment = new Enrolment();
        enrolment.setUserId(userId);
        enrolment.setCourse(course);
        enrolmentRepository.save(enrolment);

        Optional<Enrolment> found = enrolmentRepository.findByUserIdAndCourseId(userId, course.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
    }

    @Test
    void findByUserIdAndCourseId_unknownPair_returnsEmpty() {
        Optional<Enrolment> found = enrolmentRepository.findByUserIdAndCourseId(
                UUID.randomUUID(), UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void existsByUserIdAndCourseId_trueAndFalse() {
        Course course = persistCourse();
        UUID userId = UUID.randomUUID();

        Enrolment enrolment = new Enrolment();
        enrolment.setUserId(userId);
        enrolment.setCourse(course);
        enrolmentRepository.save(enrolment);

        assertThat(enrolmentRepository.existsByUserIdAndCourseId(userId, course.getId())).isTrue();
        assertThat(enrolmentRepository.existsByUserIdAndCourseId(UUID.randomUUID(), course.getId())).isFalse();
    }

    @Test
    void lessonProgressSaveAndLoadRoundTrip() {
        Lesson lesson = persistLesson();
        UUID userId = UUID.randomUUID();

        LessonProgress progress = new LessonProgress();
        progress.setUserId(userId);
        progress.setLesson(lesson);

        LessonProgress saved = lessonProgressRepository.save(progress);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(LessonProgressStatus.COMPLETED);
    }

    @Test
    void moduleUnlockSaveAndLoadRoundTrip() {
        Course course = persistCourse();
        UUID userId = UUID.randomUUID();

        Enrolment enrolment = new Enrolment();
        enrolment.setUserId(userId);
        enrolment.setCourse(course);
        enrolmentRepository.save(enrolment);

        com.courseservice.models.Module module = course.getModules().get(0);

        ModuleUnlock unlock = new ModuleUnlock();
        unlock.setEnrolment(enrolment);
        unlock.setModule(module);

        ModuleUnlock saved = moduleUnlockRepository.save(unlock);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUnlockedAt()).isNotNull();
    }

    // --- helpers ---

    private Course persistCourse() {
        Course course = new Course();
        course.setInstructorId(UUID.randomUUID());
        course.setTitle("Test Course");
        course.setVisibility(CourseVisibility.PUBLIC);

        com.courseservice.models.Module module = new com.courseservice.models.Module();
        module.setCourse(course);
        module.setTitle("Module 1");
        module.setOrderIndex(1);
        course.getModules().add(module);

        return em.persistAndFlush(course);
    }

    private Lesson persistLesson() {
        Course course = persistCourse();
        com.courseservice.models.Module module = course.getModules().get(0);

        Lesson lesson = new Lesson();
        lesson.setModule(module);
        lesson.setTitle("Lesson 1");
        lesson.setOrderIndex(1);
        lesson.setContentType(ContentType.VIDEO);
        module.getLessons().add(lesson);

        em.flush();
        return lesson;
    }
}
