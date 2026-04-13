package com.example.musicrec.controller;

import com.example.musicrec.config.security.AppPrincipal;
import com.example.musicrec.domain.Interaction;
import com.example.musicrec.dto.interaction.CreateInteractionRequest;
import com.example.musicrec.dto.interaction.InteractionResponse;
import com.example.musicrec.service.InteractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interactions")
public class InteractionController {

    private final InteractionService interactionService;

    @PostMapping
    public InteractionResponse create(@Valid @RequestBody CreateInteractionRequest req,
                                      Authentication authentication) {
        AppPrincipal user = (AppPrincipal) authentication.getPrincipal();

        Interaction i = interactionService.createForUser(user.id(), req);

        return InteractionResponse.builder()
                .id(i.getId())
                .userId(i.getUser().getId())
                .trackId(i.getTrack().getId())
                .type(i.getType().name())
                .eventTime(i.getEventTime())
                .positionMs(i.getPositionMs())
                .build();
    }
}