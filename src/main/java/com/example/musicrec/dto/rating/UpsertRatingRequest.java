package com.example.musicrec.dto.rating;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class UpsertRatingRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private UUID trackId;

    /**
     * Assumption: -1..5 allowed; validate in service.
     */
    @NotNull
    private Integer value;
}
