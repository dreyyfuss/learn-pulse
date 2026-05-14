package com.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// Full context test — requires a live MySQL instance.
// Run with the dev Docker stack: docker compose -f docker-compose.dev.yml up -d mysql
@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class ApplicationTests {

    @Test
    void contextLoads() {
    }
}
