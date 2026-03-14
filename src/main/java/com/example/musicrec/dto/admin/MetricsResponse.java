package com.example.musicrec.dto.admin;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class MetricsResponse {
    String modelVersion;
    Map<String, Object> metrics;
    String metricsJsonPath;
}
