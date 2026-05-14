package com.courseservice.repository;

import com.courseservice.enums.ContentType;
import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.models.Course;
import com.courseservice.models.Lesson;
import com.courseservice.models.Module;
import com.courseservice.repositories.CourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
class CourseRepositoryTest {

    @Autowired
    private CourseRepository courseRepository;

    @Test
    void saveAndLoadRoundTrip() {
        Course course = buildCourse("Spring Boot Basics");

        Course saved = courseRepository.save(course);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Course found = courseRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Spring Boot Basics");
        assertThat(found.getStatus()).isEqualTo(CourseStatus.DRAFT);
        assertThat(found.getVisibility()).isEqualTo(CourseVisibility.PUBLIC);
        assertThat(found.isLocked()).isFalse();
    }

    @Test
    void findWithModulesAndLessonsById_eagerLoadsGraph() {
        Course course = buildCourse("Java Fundamentals");

        Module module = new Module();
        module.setCourse(course);
        module.setTitle("Module 1");
        module.setOrderIndex(1);
        course.getModules().add(module);

        Lesson lesson = new Lesson();
        lesson.setModule(module);
        lesson.setTitle("Lesson 1");
        lesson.setOrderIndex(1);
        lesson.setContentType(ContentType.VIDEO);
        module.getLessons().add(lesson);

        courseRepository.save(course);
        courseRepository.flush();

        Optional<Course> result = courseRepository.findWithModulesAndLessonsById(course.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getModules()).hasSize(1);
        Module loadedModule = result.get().getModules().get(0);
        assertThat(loadedModule.getLessons()).hasSize(1);
        assertThat(loadedModule.getLessons().iterator().next().getTitle()).isEqualTo("Lesson 1");
    }

    private Course buildCourse(String title) {
        Course course = new Course();
        course.setInstructorId(UUID.randomUUID());
        course.setTitle(title);
        course.setVisibility(CourseVisibility.PUBLIC);
        return course;
    }
}
