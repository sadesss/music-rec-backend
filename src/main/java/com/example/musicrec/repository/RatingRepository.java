package com.example.musicrec.repository;

import com.example.musicrec.domain.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {
    Optional<Rating> findByUserIdAndTrackId(UUID userId, UUID trackId);
}
