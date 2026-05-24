package com.certservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.consumer.auto-startup=false",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:certapptests;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.s3.endpoint=",
        "app.s3.access-key=test",
        "app.s3.secret-key=test",
        "app.s3.bucket=test",
        "app.s3.region=us-east-1"
})
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
