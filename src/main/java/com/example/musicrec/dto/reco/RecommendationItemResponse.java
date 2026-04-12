package com.example.musicrec.dto.reco;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class RecommendationItemResponse {
    UUID trackId;
    String title;
    String artist;
    String album;
    String originalGenre;
    String audioUrl;

    Double score;
    Integer rank;
    String modelVersion;
    String reason;
}