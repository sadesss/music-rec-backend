package com.example.musicrec.dto.track;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class TrackResponse {
    UUID id;
    String title;
    String artist;
    String album;
    String originalGenre;
    Integer durationSeconds;

    Map<String, Object> features; // key -> value
    Instant createdAt;
}
