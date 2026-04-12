package com.example.musicrec.repository;

import com.example.musicrec.domain.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {
    List<Interaction> findTop200ByUserIdOrderByEventTimeDesc(UUID userId);
    List<Interaction> findByUserIdOrderByEventTimeDesc(UUID userId);
    long countByTrackId(UUID trackId);
}