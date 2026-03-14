package com.example.musicrec.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImportRecommendationsRequest {
    /**
     * Path to JSON file with recommendations produced by Python.
     * Example: ./data/artifacts/model/recommendations.json
     */
    @NotBlank
    private String path;
}
