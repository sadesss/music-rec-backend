package com.example.musicrec.service;

import com.example.musicrec.domain.Rating;
import com.example.musicrec.domain.Track;
import com.example.musicrec.domain.User;
import com.example.musicrec.dto.rating.UpsertRatingRequest;
import com.example.musicrec.exception.BadRequestException;
import com.example.musicrec.exception.NotFoundException;
import com.example.musicrec.repository.RatingRepository;
import com.example.musicrec.repository.TrackRepository;
import com.example.musicrec.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;

    public Rating upsertForUser(UUID userId, UpsertRatingRequest req) {
        validateValue(req.getValue());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        Track track = trackRepository.findById(req.getTrackId())
                .orElseThrow(() -> new NotFoundException("Track not found: " + req.getTrackId()));

        Rating r = ratingRepository.findByUserIdAndTrackId(user.getId(), track.getId()).orElseGet(Rating::new);
        r.setUser(user);
        r.setTrack(track);
        r.setValue(req.getValue());

        return ratingRepository.save(r);
    }

    private void validateValue(Integer v) {
        if (v == null) {
            throw new BadRequestException("Rating value is required");
        }
        if (v < 1 || v > 5) {
            throw new BadRequestException("Rating value must be in [1..5]. Got: " + v);
        }
    }
}