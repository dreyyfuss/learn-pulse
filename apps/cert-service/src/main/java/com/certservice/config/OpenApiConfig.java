package com.certservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Value("${swagger.server-url:http://localhost}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .servers(List.of(new Server().url(serverUrl).description("API Gateway (Traefik)")))
                .info(new Info()
                        .title("LearnPulse — Certificate Service API")
                        .description("""
                                Handles certificate issuance (triggered by Kafka) and learner certificate downloads.

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
    public GroupedOpenApi certificatesApi() {
        return GroupedOpenApi.builder()
                .group("1 - Certificates")
                .pathsToMatch("/api/learner/certificates", "/api/certificates/**")
                .build();
    }
}