package com.example.musicrec.controller;

import com.example.musicrec.dto.auth.AuthResponse;
import com.example.musicrec.dto.auth.LoginRequest;
import com.example.musicrec.dto.auth.RegisterRequest;
import com.example.musicrec.exception.BadRequestException;
import com.example.musicrec.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public AuthResponse me(@RequestHeader("X-Session-Token") String token) {
        return authService.me(token);
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader("X-Session-Token") String token) {
        authService.logout(token);
    }
}