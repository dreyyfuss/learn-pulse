package com.courseservice.repository;

import com.courseservice.enums.CourseVisibility;
import com.courseservice.models.Course;
import com.courseservice.models.Module;
import com.courseservice.repositories.CourseRepository;
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
class ModuleRepositoryTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ModuleRepository moduleRepository;

    @Test
    void findByCourseIdOrderByOrderIndex_returnsModulesInOrder() {
        Course course = new Course();
        course.setInstructorId(UUID.randomUUID());
        course.setTitle("Test Course");
        course.setVisibility(CourseVisibility.PUBLIC);
        courseRepository.save(course);

        Module m2 = buildModule(course, "Module 2", 2);
        Module m1 = buildModule(course, "Module 1", 1);
        moduleRepository.saveAll(List.of(m2, m1));

        List<Module> results = moduleRepository.findByCourseIdOrderByOrderIndex(course.getId());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getTitle()).isEqualTo("Module 1");
        assertThat(results.get(1).getTitle()).isEqualTo("Module 2");
    }

    private Module buildModule(Course course, String title, int orderIndex) {
        Module module = new Module();
        module.setCourse(course);
        module.setTitle(title);
        module.setOrderIndex(orderIndex);
        return module;
    }
}
