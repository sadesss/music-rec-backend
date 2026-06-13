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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;

    @Transactional
    public Rating upsertForUser(UUID userId, UpsertRatingRequest req) {
        validateValue(req.getValue());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        Track track = trackRepository.findById(req.getTrackId())
                .orElseThrow(() -> new NotFoundException("Track not found: " + req.getTrackId()));

        Rating rating = ratingRepository.findByUserIdAndTrackId(user.getId(), track.getId())
                .orElseGet(() -> {
                    Rating r = new Rating();
                    r.setUser(user);
                    r.setTrack(track);
                    return r;
                });

        rating.setValue(req.getValue());

        try {
            return ratingRepository.saveAndFlush(rating);
        } catch (DataIntegrityViolationException e) {
            Rating existing = ratingRepository.findByUserIdAndTrackId(user.getId(), track.getId())
                    .orElseThrow(() -> e);

            existing.setValue(req.getValue());
            return ratingRepository.saveAndFlush(existing);
        }
    }

    private void validateValue(Integer v) {
        if (v == null) {
            throw new BadRequestException("Rating value is required");
        }
        if (v < 1 || v > 10) {
            throw new BadRequestException("Rating value must be in [1..10]. Got: " + v);
        }
    }
}