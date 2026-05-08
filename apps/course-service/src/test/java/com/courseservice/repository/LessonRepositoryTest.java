package com.courseservice.repository;

import com.courseservice.enums.ContentType;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.models.Course;
import com.courseservice.models.Lesson;
import com.courseservice.models.Module;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.LessonRepository;
import com.courseservice.repositories.ModuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LessonRepositoryTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Test
    void findByModuleIdOrderByOrderIndex_returnsLessonsInOrder() {
        Course course = new Course();
        course.setInstructorId(UUID.randomUUID());
        course.setTitle("Test Course");
        course.setVisibility(CourseVisibility.PUBLIC);
        courseRepository.save(course);

        Module module = new Module();
        module.setCourse(course);
        module.setTitle("Module 1");
        module.setOrderIndex(1);
        moduleRepository.save(module);

        Lesson l3 = buildLesson(module, "Lesson 3", 3, ContentType.ARTICLE);
        Lesson l1 = buildLesson(module, "Lesson 1", 1, ContentType.VIDEO);
        Lesson l2 = buildLesson(module, "Lesson 2", 2, ContentType.DOCUMENT);
        lessonRepository.saveAll(List.of(l3, l1, l2));

        List<Lesson> results = lessonRepository.findByModuleIdOrderByOrderIndex(module.getId());

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getTitle()).isEqualTo("Lesson 1");
        assertThat(results.get(1).getTitle()).isEqualTo("Lesson 2");
        assertThat(results.get(2).getTitle()).isEqualTo("Lesson 3");
    }

    private Lesson buildLesson(Module module, String title, int orderIndex, ContentType contentType) {
        Lesson lesson = new Lesson();
        lesson.setModule(module);
        lesson.setTitle(title);
        lesson.setOrderIndex(orderIndex);
        lesson.setContentType(contentType);
        return lesson;
    }
}
