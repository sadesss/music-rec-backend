package com.example.musicrec.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record JamendoImportRequest(
        @NotBlank String datasetRoot,
        @NotBlank String audioRoot,
        @Min(1) int limit
) {
}
