package com.example.musicrec.service;

import com.example.musicrec.domain.Interaction;
import com.example.musicrec.domain.Track;
import com.example.musicrec.domain.User;
import com.example.musicrec.dto.interaction.CreateInteractionRequest;
import com.example.musicrec.exception.NotFoundException;
import com.example.musicrec.repository.InteractionRepository;
import com.example.musicrec.repository.TrackRepository;
import com.example.musicrec.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InteractionService {

    private final InteractionRepository interactionRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;

    public Interaction createForUser(UUID userId, CreateInteractionRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        Track track = trackRepository.findById(req.getTrackId())
                .orElseThrow(() -> new NotFoundException("Track not found: " + req.getTrackId()));

        Interaction i = new Interaction();
        i.setUser(user);
        i.setTrack(track);
        i.setType(req.getType());
        i.setEventTime(req.getEventTime() != null ? req.getEventTime() : Instant.now());
        i.setPositionMs(req.getPositionMs());
        i.setMetadataText(req.getMetadataText());

        return interactionRepository.save(i);
    }
}