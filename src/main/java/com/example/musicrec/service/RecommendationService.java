package com.example.musicrec.service;

import com.example.musicrec.domain.Recommendation;
import com.example.musicrec.domain.Track;
import com.example.musicrec.dto.reco.RecommendationItemResponse;
import com.example.musicrec.dto.reco.RecommendationResponse;
import com.example.musicrec.exception.NotFoundException;
import com.example.musicrec.repository.RecommendationRepository;
import com.example.musicrec.repository.TrackRepository;
import com.example.musicrec.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;

    /**
     * Stub logic:
     * 1) If there are stored recommendations for user -> return them by score desc.
     * 2) Else fallback to latest tracks (cold start).
     *
     * Integration point:
     * - later you can import Python-generated recommendations into DB,
     *   then this method automatically starts serving them.
     */
    public RecommendationResponse getRecommendations(UUID userId, int limit) {
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));

        List<Recommendation> stored = recommendationRepository.findTop50ByUserIdOrderByScoreDesc(userId);
        if (!stored.isEmpty()) {
            List<RecommendationItemResponse> items = stored.stream()
                    .limit(limit)
                    .map(r -> RecommendationItemResponse.builder()
                            .trackId(r.getTrack().getId())
                            .score(r.getScore())
                            .rank(r.getRank())
                            .modelVersion(r.getModelVersion())
                            .build())
                    .toList();

            return RecommendationResponse.builder()
                    .userId(userId)
                    .items(items)
                    .build();
        }

        // Cold start: return latest tracks with synthetic scores
        List<Track> latest = trackRepository.findTop50ByOrderByCreatedAtDesc();
        List<Track> chosen = latest.stream().limit(limit).toList();

        List<RecommendationItemResponse> items = IntStream.range(0, chosen.size())
                .mapToObj(i -> RecommendationItemResponse.builder()
                        .trackId(chosen.get(i).getId())
                        .score(1.0 - (i * 0.001)) // fake descending score
                        .rank(i + 1)
                        .modelVersion("stub")
                        .build())
                .toList();

        return RecommendationResponse.builder()
                .userId(userId)
                .items(items)
                .build();
    }
}
