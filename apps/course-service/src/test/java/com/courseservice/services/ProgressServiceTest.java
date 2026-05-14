package com.courseservice.services;

import com.courseservice.dto.response.ProgressResponse;
import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.exception.NotOwnerException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock EnrolmentRepository enrolmentRepository;
    @Mock CourseRepository courseRepository;
    @Mock ModuleUnlockRepository moduleUnlockRepository;
    @Mock LessonProgressRepository lessonProgressRepository;

    @InjectMocks ProgressService progressService;

    private static final UUID USER_ID      = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_ID     = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID COURSE_ID    = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ENROLMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID MODULE1_ID   = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID MODULE2_ID   = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID LESSON1_ID   = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID LESSON2_ID   = UUID.fromString("00000000-0000-0000-0000-000000000008");

    @Test
    void getProgress_owner_returnsFullTree() {
        Enrolment enrolment = buildEnrolment(USER_ID);
        Course course = buildCourseWithModulesAndLessons();

        when(enrolmentRepository.findById(ENROLMENT_ID)).thenReturn(Optional.of(enrolment));
        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.of(course));
        when(moduleUnlockRepository.findByEnrolmentId(ENROLMENT_ID)).thenReturn(List.of(buildModuleUnlock(MODULE1_ID)));
        when(lessonProgressRepository.findByUserIdAndLessonIdIn(USER_ID, List.of(LESSON1_ID, LESSON2_ID)))
                .thenReturn(Collections.emptyList());

        ProgressResponse response = progressService.getProgress(ENROLMENT_ID, USER_ID, false);

        assertThat(response.enrolmentId()).isEqualTo(ENROLMENT_ID);
        assertThat(response.courseId()).isEqualTo(COURSE_ID);
        assertThat(response.modules()).hasSize(2);
        assertThat(response.modules().get(0).unlocked()).isTrue();
        assertThat(response.modules().get(1).unlocked()).isFalse();
    }

    @Test
    void getProgress_admin_bypassesOwnerCheck() {
        Enrolment enrolment = buildEnrolment(USER_ID);
        Course course = buildCourseWithModulesAndLessons();

        when(enrolmentRepository.findById(ENROLMENT_ID)).thenReturn(Optional.of(enrolment));
        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.of(course));
        when(moduleUnlockRepository.findByEnrolmentId(ENROLMENT_ID)).thenReturn(Collections.emptyList());
        when(lessonProgressRepository.findByUserIdAndLessonIdIn(USER_ID, List.of(LESSON1_ID, LESSON2_ID)))
                .thenReturn(Collections.emptyList());

        ProgressResponse response = progressService.getProgress(ENROLMENT_ID, OTHER_ID, true);

        assertThat(response).isNotNull();
    }

    @Test
    void getProgress_instructor_courseOwner_allowed() {
        Enrolment enrolment = buildEnrolment(USER_ID);
        Course course = buildCourseWithModulesAndLessons();

        when(enrolmentRepository.findById(ENROLMENT_ID)).thenReturn(Optional.of(enrolment));
        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.of(course));
        when(moduleUnlockRepository.findByEnrolmentId(ENROLMENT_ID)).thenReturn(Collections.emptyList());
        when(lessonProgressRepository.findByUserIdAndLessonIdIn(USER_ID, List.of(LESSON1_ID, LESSON2_ID)))
                .thenReturn(Collections.emptyList());

        ProgressResponse response = progressService.getProgress(ENROLMENT_ID, INSTRUCTOR_ID, false);

        assertThat(response).isNotNull();
    }

    @Test
    void getProgress_notOwner_throws() {
        Enrolment enrolment = buildEnrolment(USER_ID);
        Course course = buildCourseWithModulesAndLessons();

        when(enrolmentRepository.findById(ENROLMENT_ID)).thenReturn(Optional.of(enrolment));
        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> progressService.getProgress(ENROLMENT_ID, OTHER_ID, false))
                .isInstanceOf(NotOwnerException.class);
    }

    @Test
    void getProgress_computesProgressPercent() {
        Enrolment enrolment = buildEnrolment(USER_ID);
        Course course = buildCourseWithModulesAndLessons();

        LessonProgress lp = new LessonProgress();
        Lesson lesson1 = buildLesson(LESSON1_ID, 1);
        lp.setLesson(lesson1);

        when(enrolmentRepository.findById(ENROLMENT_ID)).thenReturn(Optional.of(enrolment));
        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.of(course));
        when(moduleUnlockRepository.findByEnrolmentId(ENROLMENT_ID)).thenReturn(Collections.emptyList());
        when(lessonProgressRepository.findByUserIdAndLessonIdIn(USER_ID, List.of(LESSON1_ID, LESSON2_ID)))
                .thenReturn(List.of(lp));

        ProgressResponse response = progressService.getProgress(ENROLMENT_ID, USER_ID, false);

        assertThat(response.progressPercent()).isEqualTo(50);
    }

    @Test
    void getProgress_computesCurrentLessonId() {
        Enrolment enrolment = buildEnrolment(USER_ID);
        Course course = buildCourseWithModulesAndLessons();

        LessonProgress lp = new LessonProgress();
        Lesson lesson1 = buildLesson(LESSON1_ID, 1);
        lp.setLesson(lesson1);

        ModuleUnlock mu = buildModuleUnlock(MODULE1_ID);

        when(enrolmentRepository.findById(ENROLMENT_ID)).thenReturn(Optional.of(enrolment));
        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.of(course));
        when(moduleUnlockRepository.findByEnrolmentId(ENROLMENT_ID)).thenReturn(List.of(mu));
        when(lessonProgressRepository.findByUserIdAndLessonIdIn(USER_ID, List.of(LESSON1_ID, LESSON2_ID)))
                .thenReturn(List.of(lp));

        ProgressResponse response = progressService.getProgress(ENROLMENT_ID, USER_ID, false);

        // lesson1 completed, lesson2 is in module2 which is locked — no current lesson in module1
        // module1 is unlocked, lesson1 is done — no remaining lesson in module1
        // module2 is locked — so currentLessonId is null
        assertThat(response.currentLessonId()).isNull();
    }

    // --- helpers ---

    private Enrolment buildEnrolment(UUID userId) {
        Enrolment e = new Enrolment();
        e.setId(ENROLMENT_ID);
        e.setUserId(userId);
        Course c = new Course();
        c.setId(COURSE_ID);
        e.setCourse(c);
        e.setStatus(EnrolmentStatus.ACTIVE);
        return e;
    }

    private Course buildCourseWithModulesAndLessons() {
        Course c = new Course();
        c.setId(COURSE_ID);
        c.setTitle("Test Course");
        c.setInstructorId(INSTRUCTOR_ID);
        c.setVisibility(CourseVisibility.PUBLIC);
        c.setStatus(CourseStatus.PUBLISHED);

        com.courseservice.models.Module m1 = new com.courseservice.models.Module();
        m1.setId(MODULE1_ID);
        m1.setTitle("Module 1");
        m1.setOrderIndex(1);
        m1.setCourse(c);

        Lesson l1 = buildLesson(LESSON1_ID, 1);
        l1.setModule(m1);
        m1.getLessons().add(l1);

        com.courseservice.models.Module m2 = new com.courseservice.models.Module();
        m2.setId(MODULE2_ID);
        m2.setTitle("Module 2");
        m2.setOrderIndex(2);
        m2.setCourse(c);

        Lesson l2 = buildLesson(LESSON2_ID, 1);
        l2.setModule(m2);
        m2.getLessons().add(l2);

        c.getModules().add(m1);
        c.getModules().add(m2);

        return c;
    }

    private Lesson buildLesson(UUID id, int orderIndex) {
        Lesson l = new Lesson();
        l.setId(id);
        l.setTitle("Lesson " + orderIndex);
        l.setOrderIndex(orderIndex);
        return l;
    }

    private ModuleUnlock buildModuleUnlock(UUID moduleId) {
        com.courseservice.models.Module m = new com.courseservice.models.Module();
        m.setId(moduleId);
        ModuleUnlock mu = new ModuleUnlock();
        mu.setModule(m);
        return mu;
    }
}
