package com.courseservice.aspects;

import com.courseservice.dto.request.CreateModuleRequest;
import com.courseservice.dto.request.UpdateCourseRequest;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.exception.CourseAlreadyStartedException;
import com.courseservice.models.Course;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.services.CourseService;
import com.courseservice.services.ModuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:lockguardtest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class CourseLockGuardTest {

    @Autowired CourseService courseService;
    @Autowired ModuleService moduleService;
    @Autowired CourseRepository courseRepository;

    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private Course persistLockedCourse() {
        Course c = new Course();
        c.setInstructorId(INSTRUCTOR_ID);
        c.setTitle("Locked Course");
        c.setVisibility(CourseVisibility.PUBLIC);
        c.setLocked(true);
        return courseRepository.save(c);
    }

    @Test
    @Transactional
    void update_lockedCourse_throwsCourseAlreadyStartedException() {
        Course course = persistLockedCourse();

        assertThatThrownBy(() ->
                courseService.update(course.getId(),
                        new UpdateCourseRequest("New Title", null, null, null, null),
                        INSTRUCTOR_ID))
                .isInstanceOf(CourseAlreadyStartedException.class);
    }

    @Test
    @Transactional
    void publish_lockedCourse_throws() {
        Course course = persistLockedCourse();

        assertThatThrownBy(() ->
                courseService.publish(course.getId(), INSTRUCTOR_ID))
                .isInstanceOf(CourseAlreadyStartedException.class);
    }

    @Test
    @Transactional
    void createModule_lockedCourse_throws() {
        Course course = persistLockedCourse();

        assertThatThrownBy(() ->
                moduleService.create(course.getId(),
                        new CreateModuleRequest("Week 1", null, 0),
                        INSTRUCTOR_ID))
                .isInstanceOf(CourseAlreadyStartedException.class);
    }
}
