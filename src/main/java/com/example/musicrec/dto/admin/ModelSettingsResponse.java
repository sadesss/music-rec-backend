package com.example.musicrec.dto.admin;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
@Builder
public class ModelSettingsResponse {
    Map<String, Object> settings;
    Instant updatedAt;
    String message;
}
