package com.example.musicrec.repository;

import com.example.musicrec.domain.Track;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrackRepository extends JpaRepository<Track, UUID> {
    List<Track> findTop50ByOrderByCreatedAtDesc();
}
