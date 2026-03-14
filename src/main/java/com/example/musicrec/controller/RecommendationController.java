package com.example.musicrec.controller;

import com.example.musicrec.dto.reco.RecommendationResponse;
import com.example.musicrec.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public RecommendationResponse get(@RequestParam UUID userId,
                                      @RequestParam(defaultValue = "20") int limit) {
        int bounded = Math.max(1, Math.min(limit, 50));
        return recommendationService.getRecommendations(userId, bounded);
    }
}
