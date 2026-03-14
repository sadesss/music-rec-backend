package com.example.musicrec.service.admin;

import com.example.musicrec.domain.Interaction;
import com.example.musicrec.domain.Rating;
import com.example.musicrec.domain.Track;
import com.example.musicrec.repository.InteractionRepository;
import com.example.musicrec.repository.RatingRepository;
import com.example.musicrec.repository.TrackRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports minimal training data for offline Python training.
 *
 * Integration point:
 * - adapt schema of JSONL lines to what your ML code expects.
 */
@Service
@RequiredArgsConstructor
public class TrainingDataExportService {

    private final InteractionRepository interactionRepository;
    private final RatingRepository ratingRepository;
    private final TrackRepository trackRepository;
    private final ObjectMapper objectMapper;

    public void exportTrainingData(Path outputJsonl) {
        try {
            Files.createDirectories(outputJsonl.getParent());

            // In a real system you'd export full dataset.
            // Here: export all tracks, all ratings, and a limited number of interactions.
            List<Track> tracks = trackRepository.findAll();
            List<Rating> ratings = ratingRepository.findAll();
            List<Interaction> interactions = interactionRepository.findAll();

            try (BufferedWriter bw = Files.newBufferedWriter(outputJsonl)) {
                for (Track t : tracks) {
                    bw.write(objectMapper.writeValueAsString(new TrackLine(t)));
                    bw.newLine();
                }
                for (Rating r : ratings) {
                    bw.write(objectMapper.writeValueAsString(new RatingLine(r)));
                    bw.newLine();
                }
                for (Interaction i : interactions) {
                    bw.write(objectMapper.writeValueAsString(new InteractionLine(i)));
                    bw.newLine();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to export training data: " + e.getMessage(), e);
        }
    }

    record TrackLine(String type, String trackId, String title, String artist, String genre) {
        TrackLine(Track t) { this("track", t.getId().toString(), t.getTitle(), t.getArtist(), t.getOriginalGenre()); }
    }

    record RatingLine(String type, String userId, String trackId, Integer value) {
        RatingLine(Rating r) { this("rating", r.getUser().getId().toString(), r.getTrack().getId().toString(), r.getValue()); }
    }

    record InteractionLine(String type, String userId, String trackId, String eventType, String eventTime) {
        InteractionLine(Interaction i) { this("interaction", i.getUser().getId().toString(), i.getTrack().getId().toString(), i.getType().name(), i.getEventTime().toString()); }
    }
}
