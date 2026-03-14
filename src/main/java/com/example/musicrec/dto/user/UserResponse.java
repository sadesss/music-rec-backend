package com.example.musicrec.dto.user;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class UserResponse {
    UUID id;
    String displayName;
    String role;
    Instant createdAt;
}
