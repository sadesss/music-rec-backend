package com.example.musicrec.dto.reco;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class RecommendationResponse {
    UUID userId;
    List<RecommendationItemResponse> items;
}
