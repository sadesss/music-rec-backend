package com.example.musicrec.dto.interaction;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class InteractionResponse {
    UUID id;
    UUID userId;
    UUID trackId;
    String type;
    Instant eventTime;
    Long positionMs;
}
