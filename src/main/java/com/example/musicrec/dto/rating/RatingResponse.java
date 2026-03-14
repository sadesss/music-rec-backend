package com.example.musicrec.dto.rating;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class RatingResponse {
    UUID id;
    UUID userId;
    UUID trackId;
    Integer value;
}
