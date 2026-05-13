package com.courseservice.services;

import com.courseservice.dto.response.PlatformAnalyticsResponse;
import com.courseservice.dto.response.PlatformAnalyticsResponse.ByRole;
import com.courseservice.dto.response.PlatformAnalyticsResponse.ByStatus;
import com.courseservice.dto.response.PlatformAnalyticsResponse.CourseStats;
import com.courseservice.dto.response.PlatformAnalyticsResponse.EnrolmentStats;
import com.courseservice.dto.response.PlatformAnalyticsResponse.UserStats;
import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.EnrolmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private final RestTemplate restTemplate;
    private final CourseRepository courseRepository;
    private final EnrolmentRepository enrolmentRepository;

    @Value("${services.user-service.url:http://user-service:8081}")
    private String userServiceUrl;

    @Cacheable(cacheNames = "analytics:admin", key = "'platform'")
    public PlatformAnalyticsResponse getAnalytics() {
        UserStatsResponse u = restTemplate.getForObject(
                userServiceUrl + "/internal/user-stats", UserStatsResponse.class);

        long totalUsers      = u != null ? u.totalUsers() : 0;
        long learners        = u != null ? u.learners()   : 0;
        long instructors     = u != null ? u.instructors(): 0;
        long admins          = u != null ? u.admins()     : 0;

        long totalCourses     = courseRepository.count();
        long publishedCourses = courseRepository.countByStatus(CourseStatus.PUBLISHED);
        long draftCourses     = totalCourses - publishedCourses;

        long totalEnrolments = enrolmentRepository.count();
        long completed       = enrolmentRepository.countByStatus(EnrolmentStatus.COMPLETED);
        double rate          = totalEnrolments == 0 ? 0.0
                : Math.round(completed * 10000.0 / totalEnrolments) / 100.0;

        return new PlatformAnalyticsResponse(
                new UserStats(totalUsers, new ByRole(learners, instructors, admins)),
                new CourseStats(totalCourses, new ByStatus(draftCourses, publishedCourses)),
                new EnrolmentStats(totalEnrolments, completed, rate)
        );
    }

    private record UserStatsResponse(
            long totalUsers,
            long learners,
            long instructors,
            long admins,
            long activeUsers,
            long suspendedUsers
    ) {}
}
