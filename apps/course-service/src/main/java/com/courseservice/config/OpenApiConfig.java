package com.courseservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LearnPulse — Course Service API")
                        .description("""
                                Manages courses, modules, lessons, enrolments, learner progress, \
                                content uploads, and analytics.

                                **Auth:** All endpoints require a valid JWT issued by the User Service. \
                                Paste the `accessToken` into the Authorize dialog below (without the `Bearer ` prefix).
                                """)
                        .version("1.0")
                        .contact(new Contact()
                                .name("LearnPulse")
                                .url("https://github.com/dreyyfuss/learn-pulse")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by User Service POST /api/auth/login")));
    }

    @Bean
    public GroupedOpenApi coursesApi() {
        return GroupedOpenApi.builder()
                .group("1 - Courses")
                .pathsToMatch("/api/courses/**")
                .build();
    }

    @Bean
    public GroupedOpenApi enrolmentsApi() {
        return GroupedOpenApi.builder()
                .group("2 - Enrolments")
                .pathsToMatch("/api/enrolments/**")
                .build();
    }

    @Bean
    public GroupedOpenApi learnerApi() {
        return GroupedOpenApi.builder()
                .group("3 - Learner")
                .pathsToMatch("/api/learner/**", "/api/lessons/**")
                .build();
    }

    @Bean
    public GroupedOpenApi instructorApi() {
        return GroupedOpenApi.builder()
                .group("4 - Instructor")
                .pathsToMatch("/api/instructor/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminCourseApi() {
        return GroupedOpenApi.builder()
                .group("5 - Admin")
                .pathsToMatch("/api/admin/**")
                .build();
    }
}