package com.example.musicrec.service;

import com.example.musicrec.domain.User;
import com.example.musicrec.domain.UserSession;
import com.example.musicrec.dto.auth.AuthResponse;
import com.example.musicrec.dto.auth.LoginRequest;
import com.example.musicrec.dto.auth.RegisterRequest;
import com.example.musicrec.exception.BadRequestException;
import com.example.musicrec.exception.NotFoundException;
import com.example.musicrec.repository.UserRepository;
import com.example.musicrec.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = normalizeEmail(req.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Пользователь с таким email уже существует");
        }

        User user = new User();
        user.setDisplayName(req.getDisplayName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user = userRepository.save(user);

        return createSession(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = normalizeEmail(req.getEmail());

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadRequestException("Неверный email или пароль"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Неверный email или пароль");
        }

        return createSession(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse me(String token) {
        UserSession session = userSessionRepository.findBySessionToken(token)
                .orElseThrow(() -> new NotFoundException("Сессия не найдена"));

        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Сессия истекла");
        }

        User user = session.getUser();

        return AuthResponse.builder()
                .userId(user.getId())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .sessionToken(session.getSessionToken())
                .sessionExpiresAt(session.getExpiresAt())
                .build();
    }

    @Transactional
    public void logout(String token) {
        userSessionRepository.deleteBySessionToken(token);
    }

    private AuthResponse createSession(User user) {
        userSessionRepository.deleteByExpiresAtBefore(Instant.now());

        UserSession session = new UserSession();
        session.setUser(user);
        session.setSessionToken(UUID.randomUUID().toString() + UUID.randomUUID().toString().replace("-", ""));
        session.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        session = userSessionRepository.save(session);

        return AuthResponse.builder()
                .userId(user.getId())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .sessionToken(session.getSessionToken())
                .sessionExpiresAt(session.getExpiresAt())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}