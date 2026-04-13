package com.example.musicrec.repository;

import com.example.musicrec.domain.UserSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @EntityGraph(attributePaths = {"user"})
    Optional<UserSession> findBySessionToken(String sessionToken);

    void deleteBySessionToken(String sessionToken);

    void deleteByExpiresAtBefore(Instant instant);
}