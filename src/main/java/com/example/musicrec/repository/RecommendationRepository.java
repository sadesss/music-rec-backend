package com.example.musicrec.repository;

import com.example.musicrec.domain.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {
    List<Recommendation> findTop50ByUserIdOrderByScoreDesc(UUID userId);
}
