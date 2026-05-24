package com.courseservice.aspects;

import com.courseservice.exception.CourseAlreadyStartedException;
import com.courseservice.repositories.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class CourseLockGuard {

    private final CourseRepository courseRepository;

    @Around(
        "execution(* com.courseservice.services.CourseService.update(..)) || " +
        "execution(* com.courseservice.services.CourseService.publish(..)) || " +
        "execution(* com.courseservice.services.ModuleService.*(..)) || " +
        "execution(* com.courseservice.services.LessonService.*(..)) || " +
        "execution(* com.courseservice.services.QuizService.create(..)) || " +
        "execution(* com.courseservice.services.QuizService.update(..)) || " +
        "execution(* com.courseservice.services.QuizService.delete(..)) || " +
        "execution(* com.courseservice.services.QuizService.upsertQuestions(..))"
    )
    public Object guardLocked(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        if (args.length > 0 && args[0] instanceof UUID courseId) {
            courseRepository.findById(courseId).ifPresent(course -> {
                if (course.isLocked()) {
                    throw new CourseAlreadyStartedException(
                            "Course is locked and cannot be modified.");
                }
            });
        }
        return pjp.proceed();
    }
}
