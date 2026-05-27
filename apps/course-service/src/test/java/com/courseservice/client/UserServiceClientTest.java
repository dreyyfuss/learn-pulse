package com.courseservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceClientTest {

    @Mock RestTemplate restTemplate;

    UserServiceClient client;

    private static final UUID ID1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        client = new UserServiceClient(restTemplate);
        ReflectionTestUtils.setField(client, "userServiceUrl", "http://user-service:8081");
    }

    @Test
    void getNames_emptyIds_returnsEmptyMapWithoutCallingHttp() {
        Map<String, String> result = client.getNames(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getNames_validIds_returnsNamesFromResponse() {
        Map<String, String> body = Map.of(
                ID1.toString(), "Alice",
                ID2.toString(), "Bob"
        );
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(body));

        Map<String, String> result = client.getNames(List.of(ID1, ID2));

        assertThat(result).containsEntry(ID1.toString(), "Alice")
                          .containsEntry(ID2.toString(), "Bob");
    }

    @Test
    void getNames_nullResponseBody_returnsEmptyMap() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));

        Map<String, String> result = client.getNames(List.of(ID1));

        assertThat(result).isEmpty();
    }

    @Test
    void getNames_httpFailure_returnsEmptyMapGracefully() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("connection refused"));

        Map<String, String> result = client.getNames(List.of(ID1));

        assertThat(result).isEmpty();
    }

    @Test
    void getNames_idsAreJoinedInQueryParam() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of()));

        client.getNames(List.of(ID1, ID2));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(
                urlCaptor.capture(),
                eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));
        assertThat(urlCaptor.getValue())
                .contains(ID1.toString())
                .contains(ID2.toString());
    }
}
