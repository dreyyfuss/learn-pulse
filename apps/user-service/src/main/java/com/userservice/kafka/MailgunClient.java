package com.userservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class MailgunClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String domain;
    private final String from;

    public MailgunClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${mailgun.api-key:}") String apiKey,
            @Value("${mailgun.domain:sandbox.mailgun.org}") String domain,
            @Value("${mailgun.from:noreply@learnpulse.dev}") String from) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.domain = domain;
        this.from = from;
    }

    public void send(String toEmail, String toName, String template, Map<String, Object> vars) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Mailgun API key not configured — skipping email to={} template={}", toEmail, template);
            return;
        }

        String credentials = Base64.getEncoder().encodeToString(("api:" + apiKey).getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + credentials);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("from", from);
        body.add("to", String.format("%s <%s>", toName, toEmail));
        body.add("template", template);
        try {
            body.add("h:X-Mailgun-Variables", objectMapper.writeValueAsString(vars));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Mailgun template variables", e);
        }

        String url = "https://api.mailgun.net/v3/" + domain + "/messages";
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Mailgun returned " + response.getStatusCode() + " for template " + template);
        }

        log.info("Email sent to={} template={}", toEmail, template);
    }
}
