package com.courseservice.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.getInterceptors().add((request, body, execution) -> {
            String traceId = MDC.get("traceId");
            if (traceId != null) {
                request.getHeaders().set("X-Trace-Id", traceId);
            }
            return execution.execute(request, body);
        });
        return rt;
    }
}