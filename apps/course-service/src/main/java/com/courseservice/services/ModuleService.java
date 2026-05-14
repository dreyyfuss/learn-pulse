package com.courseservice.services;

import com.courseservice.dto.request.CreateModuleRequest;
import com.courseservice.dto.request.ReorderModulesRequest;
import com.courseservice.dto.request.UpdateModuleRequest;
import com.courseservice.dto.response.ModuleDetailResponse;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Course;
import com.courseservice.models.Module;
import com.courseservice.repositories.ModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private final CourseService courseService;
    private final ModuleRepository moduleRepository;

    @Transactional
    public ModuleDetailResponse create(UUID courseId, CreateModuleRequest req, UUID instructorId) {
        Course course = courseService.loadAndGuard(courseId, instructorId);

        Module module = new Module();
        module.setCourse(course);
        module.setTitle(req.title());
        module.setDescription(req.description());
        module.setOrderIndex(req.orderIndex());

        return ModuleDetailResponse.from(moduleRepository.save(module));
    }

    @Transactional
    public ModuleDetailResponse update(UUID courseId, UUID moduleId, UpdateModuleRequest req, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        Module module = loadModuleInCourse(moduleId, courseId);

        if (req.title() != null)       module.setTitle(req.title());
        if (req.description() != null) module.setDescription(req.description());
        if (req.orderIndex() != null)  module.setOrderIndex(req.orderIndex());

        return ModuleDetailResponse.from(moduleRepository.save(module));
    }

    @Transactional
    public void reorder(UUID courseId, ReorderModulesRequest req, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        for (var item : req.modules()) {
            moduleRepository.updateOrderIndex(item.id(), item.orderIndex());
        }
    }

    @Transactional
    public void delete(UUID courseId, UUID moduleId, UUID instructorId) {
        courseService.loadAndGuard(courseId, instructorId);
        Module module = loadModuleInCourse(moduleId, courseId);
        moduleRepository.delete(module);
    }

    private Module loadModuleInCourse(UUID moduleId, UUID courseId) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + moduleId));
        if (!module.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Module " + moduleId + " does not belong to course " + courseId);
        }
        return module;
    }
}
