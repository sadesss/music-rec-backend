package com.example.musicrec.dto.interaction;

import com.example.musicrec.domain.enums.InteractionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class CreateInteractionRequest {

    @NotNull
    private UUID trackId;

    @NotNull
    private InteractionType type;

    /**
     * Optional; if null - server uses Instant.now().
     */
    private Instant eventTime;

    private Long positionMs;

    private String metadataText;
}