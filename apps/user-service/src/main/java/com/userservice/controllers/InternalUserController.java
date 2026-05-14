package com.userservice.controllers;

import com.userservice.domain.user.User;
import com.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserRepository userRepository;

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> getUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(user -> ResponseEntity.ok(Map.of(
                        "fullName", user.getFullName(),
                        "email", user.getEmail()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users/batch")
    public ResponseEntity<Map<String, String>> getUsers(@RequestParam String ids) {
        List<UUID> uuids = Arrays.stream(ids.split(","))
                .map(String::trim)
                .map(UUID::fromString)
                .collect(Collectors.toList());
        Map<String, String> result = userRepository.findAllById(uuids)
                .stream()
                .collect(Collectors.toMap(u -> u.getId().toString(), User::getFullName));
        return ResponseEntity.ok(result);
    }
}
