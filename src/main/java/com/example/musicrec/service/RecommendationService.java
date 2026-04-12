package com.example.musicrec.service;

import com.example.musicrec.domain.Interaction;
import com.example.musicrec.domain.Rating;
import com.example.musicrec.domain.Recommendation;
import com.example.musicrec.domain.Track;
import com.example.musicrec.domain.TrackFeature;
import com.example.musicrec.dto.reco.RecommendationItemResponse;
import com.example.musicrec.dto.reco.RecommendationResponse;
import com.example.musicrec.exception.NotFoundException;
import com.example.musicrec.repository.InteractionRepository;
import com.example.musicrec.repository.RatingRepository;
import com.example.musicrec.repository.RecommendationRepository;
import com.example.musicrec.repository.TrackFeatureRepository;
import com.example.musicrec.repository.TrackRepository;
import com.example.musicrec.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;
    private final RatingRepository ratingRepository;
    private final InteractionRepository interactionRepository;
    private final TrackFeatureRepository trackFeatureRepository;

    public RecommendationResponse getRecommendations(UUID userId, int limit) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        // 1) Если есть заранее импортированные рекомендации — используем их
        List<Recommendation> stored = recommendationRepository.findTop50ByUserIdOrderByScoreDesc(userId);
        if (!stored.isEmpty()) {
            List<RecommendationItemResponse> items = stored.stream()
                    .limit(limit)
                    .map(r -> RecommendationItemResponse.builder()
                            .trackId(r.getTrack().getId())
                            .title(r.getTrack().getTitle())
                            .artist(r.getTrack().getArtist())
                            .album(r.getTrack().getAlbum())
                            .originalGenre(r.getTrack().getOriginalGenre())
                            .audioUrl("/api/v1/tracks/" + r.getTrack().getId() + "/stream")
                            .score(r.getScore())
                            .rank(r.getRank())
                            .modelVersion(r.getModelVersion())
                            .reason("precomputed")
                            .build())
                    .toList();

            return RecommendationResponse.builder()
                    .userId(userId)
                    .items(items)
                    .build();
        }

        // 2) Иначе считаем online-рекомендации из истории пользователя
        List<Rating> ratings = ratingRepository.findByUserId(userId);
        List<Interaction> interactions = interactionRepository.findByUserIdOrderByEventTimeDesc(userId);

        Map<UUID, Integer> explicitRatings = ratings.stream()
                .collect(Collectors.toMap(r -> r.getTrack().getId(), Rating::getValue, (a, b) -> b));

        Set<UUID> dislikedTrackIds = ratings.stream()
                .filter(r -> r.getValue() < 0)
                .map(r -> r.getTrack().getId())
                .collect(Collectors.toSet());

        Set<UUID> seenTrackIds = new HashSet<>();
        interactions.forEach(i -> seenTrackIds.add(i.getTrack().getId()));
        ratings.forEach(r -> seenTrackIds.add(r.getTrack().getId()));

        Map<UUID, Double> positiveTrackWeights = new HashMap<>();

        for (Rating r : ratings) {
            if (r.getValue() > 0) {
                positiveTrackWeights.merge(r.getTrack().getId(), 2.0 + r.getValue(), Double::sum);
            }
        }

        for (Interaction i : interactions) {
            double w = switch (i.getType()) {
                case PLAY -> 1.0;
                case PAUSE -> 0.2;
                case SKIP -> -1.0;
                case FINISH -> 2.0;
                case LIKE -> 3.0;
                case DISLIKE -> -3.0;
            };
            positiveTrackWeights.merge(i.getTrack().getId(), w, Double::sum);
        }

        List<Track> allTracks = trackRepository.findAll();
        Map<UUID, Track> trackById = allTracks.stream()
                .collect(Collectors.toMap(Track::getId, Function.identity()));

        List<UUID> positiveTrackIds = positiveTrackWeights.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .filter(trackById::containsKey)
                .toList();

        Map<String, Double> artistPref = new HashMap<>();
        Map<String, Double> genrePref = new HashMap<>();
        Map<String, Double> albumPref = new HashMap<>();

        for (UUID tid : positiveTrackIds) {
            Track t = trackById.get(tid);
            double w = positiveTrackWeights.getOrDefault(tid, 0.0);

            if (t.getArtist() != null && !t.getArtist().isBlank()) {
                artistPref.merge(t.getArtist().trim().toLowerCase(), w, Double::sum);
            }
            if (t.getOriginalGenre() != null && !t.getOriginalGenre().isBlank()) {
                genrePref.merge(t.getOriginalGenre().trim().toLowerCase(), w, Double::sum);
            }
            if (t.getAlbum() != null && !t.getAlbum().isBlank()) {
                albumPref.merge(t.getAlbum().trim().toLowerCase(), w, Double::sum);
            }
        }

        Map<UUID, Map<String, Double>> featuresByTrack = loadNumericFeatures(allTracks);

        // Профиль пользователя по audio features
        Map<String, Double> profile = buildUserFeatureProfile(positiveTrackIds, positiveTrackWeights, featuresByTrack);

        List<ScoredTrack> scored = new ArrayList<>();

        for (Track candidate : allTracks) {
            UUID candidateId = candidate.getId();

            if (dislikedTrackIds.contains(candidateId)) {
                continue;
            }

            // для demo лучше не рекомендовать уже оцененные/прослушанные
            if (seenTrackIds.contains(candidateId)) {
                continue;
            }

            double score = 0.0;
            List<String> reasons = new ArrayList<>();

            String artist = norm(candidate.getArtist());
            String genre = norm(candidate.getOriginalGenre());
            String album = norm(candidate.getAlbum());

            double artistScore = artist != null ? artistPref.getOrDefault(artist, 0.0) * 1.8 : 0.0;
            double genreScore = genre != null ? genrePref.getOrDefault(genre, 0.0) * 1.2 : 0.0;
            double albumScore = album != null ? albumPref.getOrDefault(album, 0.0) * 0.8 : 0.0;

            if (artistScore > 0) reasons.add("artist");
            if (genreScore > 0) reasons.add("genre");
            if (albumScore > 0) reasons.add("album");

            score += artistScore + genreScore + albumScore;

            Map<String, Double> candFeatures = featuresByTrack.getOrDefault(candidateId, Map.of());
            double featureScore = featureSimilarity(profile, candFeatures);
            if (featureScore > 0) {
                score += featureScore * 2.5;
                reasons.add("audio-features");
            }

            long playPopularity = interactionRepository.countByTrackId(candidateId);
            long ratingPopularity = ratingRepository.countByTrackIdAndValueGreaterThan(candidateId, 0);
            double popularityScore = Math.log1p(playPopularity) * 0.15 + Math.log1p(ratingPopularity) * 0.35;
            score += popularityScore;

            if (score > 0) {
                scored.add(new ScoredTrack(candidate, score, reasons.isEmpty() ? "popular" : String.join(", ", reasons)));
            }
        }

        if (scored.isEmpty()) {
            // Совсем cold start — просто latest unseen
            List<RecommendationItemResponse> items = trackRepository.findTop50ByOrderByCreatedAtDesc().stream()
                    .filter(t -> !seenTrackIds.contains(t.getId()))
                    .limit(limit)
                    .map(t -> RecommendationItemResponse.builder()
                            .trackId(t.getId())
                            .title(t.getTitle())
                            .artist(t.getArtist())
                            .album(t.getAlbum())
                            .originalGenre(t.getOriginalGenre())
                            .audioUrl("/api/v1/tracks/" + t.getId() + "/stream")
                            .score(0.1)
                            .modelVersion("online-fallback-v1")
                            .reason("latest")
                            .build())
                    .toList();

            return RecommendationResponse.builder()
                    .userId(userId)
                    .items(withRanks(items))
                    .build();
        }

        List<RecommendationItemResponse> items = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredTrack::score).reversed())
                .limit(limit)
                .map(s -> RecommendationItemResponse.builder()
                        .trackId(s.track().getId())
                        .title(s.track().getTitle())
                        .artist(s.track().getArtist())
                        .album(s.track().getAlbum())
                        .originalGenre(s.track().getOriginalGenre())
                        .audioUrl("/api/v1/tracks/" + s.track().getId() + "/stream")
                        .score(s.score())
                        .modelVersion("online-content-cf-v1")
                        .reason(s.reason())
                        .build())
                .toList();

        return RecommendationResponse.builder()
                .userId(userId)
                .items(withRanks(items))
                .build();
    }

    private List<RecommendationItemResponse> withRanks(List<RecommendationItemResponse> items) {
        List<RecommendationItemResponse> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            RecommendationItemResponse item = items.get(i);
            out.add(RecommendationItemResponse.builder()
                    .trackId(item.getTrackId())
                    .title(item.getTitle())
                    .artist(item.getArtist())
                    .album(item.getAlbum())
                    .originalGenre(item.getOriginalGenre())
                    .audioUrl(item.getAudioUrl())
                    .score(item.getScore())
                    .rank(i + 1)
                    .modelVersion(item.getModelVersion())
                    .reason(item.getReason())
                    .build());
        }
        return out;
    }

    private Map<UUID, Map<String, Double>> loadNumericFeatures(List<Track> tracks) {
        List<UUID> ids = tracks.stream().map(Track::getId).toList();
        List<TrackFeature> features = trackFeatureRepository.findByTrackIdIn(ids);

        Map<UUID, Map<String, Double>> result = new HashMap<>();
        for (TrackFeature f : features) {
            if (f.getValueNumber() == null) continue;
            result.computeIfAbsent(f.getTrack().getId(), k -> new HashMap<>())
                    .put(f.getFeatureKey(), f.getValueNumber());
        }
        return result;
    }

    private Map<String, Double> buildUserFeatureProfile(
            List<UUID> positiveTrackIds,
            Map<UUID, Double> weights,
            Map<UUID, Map<String, Double>> featuresByTrack
    ) {
        List<String> keys = List.of("tempo_bpm", "rms_energy", "spectral_centroid_hz");

        Map<String, Double> sums = new HashMap<>();
        Map<String, Double> sumWeights = new HashMap<>();

        for (UUID tid : positiveTrackIds) {
            Map<String, Double> feats = featuresByTrack.get(tid);
            if (feats == null) continue;

            double w = Math.max(0.1, weights.getOrDefault(tid, 1.0));

            for (String k : keys) {
                Double v = feats.get(k);
                if (v == null) continue;
                sums.merge(k, v * w, Double::sum);
                sumWeights.merge(k, w, Double::sum);
            }
        }

        Map<String, Double> profile = new HashMap<>();
        for (String k : keys) {
            Double s = sums.get(k);
            Double w = sumWeights.get(k);
            if (s != null && w != null && w > 0) {
                profile.put(k, s / w);
            }
        }
        return profile;
    }

    private double featureSimilarity(Map<String, Double> profile, Map<String, Double> candidate) {
        if (profile.isEmpty() || candidate.isEmpty()) return 0.0;

        double tempo = closeness(profile.get("tempo_bpm"), candidate.get("tempo_bpm"), 40.0);
        double rms = closeness(profile.get("rms_energy"), candidate.get("rms_energy"), 0.15);
        double centroid = closeness(profile.get("spectral_centroid_hz"), candidate.get("spectral_centroid_hz"), 2500.0);

        double sum = 0.0;
        int cnt = 0;
        if (tempo >= 0) { sum += tempo; cnt++; }
        if (rms >= 0) { sum += rms; cnt++; }
        if (centroid >= 0) { sum += centroid; cnt++; }

        return cnt == 0 ? 0.0 : sum / cnt;
    }

    private double closeness(Double a, Double b, double scale) {
        if (a == null || b == null) return -1.0;
        double d = Math.abs(a - b);
        return Math.max(0.0, 1.0 - (d / scale));
    }

    private String norm(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase();
    }

    private record ScoredTrack(Track track, double score, String reason) {}
}