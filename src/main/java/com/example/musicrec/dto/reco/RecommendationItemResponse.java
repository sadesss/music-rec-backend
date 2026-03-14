package com.example.musicrec.dto.reco;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class RecommendationItemResponse {
    UUID trackId;
    Double score;
    Integer rank;
    String modelVersion;
}
