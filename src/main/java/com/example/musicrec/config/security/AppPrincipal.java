package com.example.musicrec.config.security;

import com.example.musicrec.domain.enums.UserRole;

import java.util.UUID;

public record AppPrincipal(
        UUID id,
        String email,
        String displayName,
        UserRole role
) {
}