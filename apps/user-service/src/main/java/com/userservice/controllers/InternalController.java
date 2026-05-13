package com.userservice.controllers;

import com.userservice.dto.response.UserStatsResponse;
import com.userservice.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final AdminUserService adminUserService;

    @GetMapping("/user-stats")
    public UserStatsResponse getUserStats() {
        return adminUserService.getUserStats();
    }
}
