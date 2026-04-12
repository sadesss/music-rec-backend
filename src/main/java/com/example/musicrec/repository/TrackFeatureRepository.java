package com.example.musicrec.repository;

import com.example.musicrec.domain.TrackFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackFeatureRepository extends JpaRepository<TrackFeature, UUID> {
    List<TrackFeature> findByTrackId(UUID trackId);
    Optional<TrackFeature> findByTrackIdAndFeatureKey(UUID trackId, String featureKey);

    List<TrackFeature> findByTrackIdIn(List<UUID> trackIds);
}