package com.example.musicrec.controller;

import com.example.musicrec.domain.User;
import com.example.musicrec.dto.user.CreateUserRequest;
import com.example.musicrec.dto.user.UserResponse;
import com.example.musicrec.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @PostMapping
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        User u = userService.create(req);
        return UserResponse.builder()
                .id(u.getId())
                .displayName(u.getDisplayName())
                .role(u.getRole().name())
                .createdAt(u.getCreatedAt())
                .build();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        User u = userService.get(id);
        return UserResponse.builder()
                .id(u.getId())
                .displayName(u.getDisplayName())
                .role(u.getRole().name())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
