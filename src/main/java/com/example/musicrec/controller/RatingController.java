package com.example.musicrec.controller;

import com.example.musicrec.domain.Rating;
import com.example.musicrec.dto.rating.RatingResponse;
import com.example.musicrec.dto.rating.UpsertRatingRequest;
import com.example.musicrec.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ratings")
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    public RatingResponse upsert(@Valid @RequestBody UpsertRatingRequest req) {
        Rating r = ratingService.upsert(req);
        return RatingResponse.builder()
                .id(r.getId())
                .userId(r.getUser().getId())
                .trackId(r.getTrack().getId())
                .value(r.getValue())
                .build();
    }
}
