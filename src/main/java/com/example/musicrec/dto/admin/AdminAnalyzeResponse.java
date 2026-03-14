package com.example.musicrec.dto.admin;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class AdminAnalyzeResponse {
    UUID trackId;
    int featuresUpserted;
    String featuresJsonPath;
}
