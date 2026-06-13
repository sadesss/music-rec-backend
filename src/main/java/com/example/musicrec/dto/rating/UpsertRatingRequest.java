package com.example.musicrec.dto.rating;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class UpsertRatingRequest {

    @NotNull
    private UUID trackId;

    /**
     * Allowed: 1..10
     */
    @NotNull
    private Integer value;
}