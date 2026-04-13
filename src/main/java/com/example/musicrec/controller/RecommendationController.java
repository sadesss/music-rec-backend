package com.example.musicrec.controller;

import com.example.musicrec.config.security.AppPrincipal;
import com.example.musicrec.dto.reco.RecommendationResponse;
import com.example.musicrec.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/me")
    public RecommendationResponse getMyRecommendations(Authentication authentication,
                                                       @RequestParam(defaultValue = "20") int limit) {
        AppPrincipal user = (AppPrincipal) authentication.getPrincipal();
        int bounded = Math.max(1, Math.min(limit, 50));
        return recommendationService.getRecommendations(user.id(), bounded);
    }
}