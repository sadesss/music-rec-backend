package com.example.musicrec.dto.auth;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class AuthResponse {
    UUID userId;
    String displayName;
    String email;
    String role;
    String sessionToken;
    Instant sessionExpiresAt;
}