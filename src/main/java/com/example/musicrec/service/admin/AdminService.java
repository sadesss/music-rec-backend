package com.example.musicrec.service.admin;

import com.example.musicrec.domain.Track;
import com.example.musicrec.domain.TrackFeature;
import com.example.musicrec.domain.enums.FeatureSource;
import com.example.musicrec.dto.admin.AdminTrackMetadataRequest;
import com.example.musicrec.exception.BadRequestException;
import com.example.musicrec.repository.TrackFeatureRepository;
import com.example.musicrec.repository.TrackRepository;
import com.example.musicrec.service.TrackService;
import com.example.musicrec.service.artifacts.ArtifactService;
import com.example.musicrec.service.python.PythonRunner;
import com.example.musicrec.service.storage.FileStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final TrackService trackService;
    private final TrackRepository trackRepository;
    private final TrackFeatureRepository trackFeatureRepository;

    private final ArtifactService artifactService;
    private final PythonRunner pythonRunner;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    private final TrainingDataExportService trainingDataExportService;
    private final RecommendationImportService recommendationImportService;

    public Track uploadTrack(AdminTrackMetadataRequest metadata, MultipartFile mp3) {
        return trackService.createFromUpload(metadata, mp3);
    }

    public AnalyzeResult analyzeTrack(UUID trackId) {
        artifactService.ensureDirs();

        Track track = trackService.get(trackId);
        Path mp3AbsPath = fileStorageService.resolveAudioPath(track.getAudioKey());
        Path featuresJson = artifactService.featuresJsonPath(trackId);

        List<String> args = List.of(
                "--input-mp3", mp3AbsPath.toString(),
                "--output-json", featuresJson.toString(),
                "--track-id", trackId.toString()
        );

        pythonRunner.runScript("analyze_track.py", args);

        int upserted = upsertFeaturesFromJson(track, featuresJson);
        return new AnalyzeResult(upserted, featuresJson.toString());
    }

    public TrainResult trainModel(String notes) {
        artifactService.ensureDirs();

        Path trainingData = artifactService.trainingDataPath();
        trainingDataExportService.exportTrainingData(trainingData);

        Path outModelDir = artifactService.modelDir();
        List<String> args = List.of(
                "--training-data", trainingData.toString(),
                "--output-dir", outModelDir.toString(),
                "--notes", notes == null ? "" : notes
        );

        pythonRunner.runScript("train.py", args);

        Map<String, Object> metrics = artifactService.readMetricsOrEmpty();
        String modelVersion = artifactService.readModelVersionOrUnknown();
        return new TrainResult(modelVersion, metrics, artifactService.metricsPath().toString());
    }

    public MetricsResult readMetrics() {
        artifactService.ensureDirs();
        Map<String, Object> metrics = artifactService.readMetricsOrEmpty();
        String modelVersion = artifactService.readModelVersionOrUnknown();
        return new MetricsResult(modelVersion, metrics, artifactService.metricsPath().toString());
    }

    public int importRecommendations(String path) {
        return recommendationImportService.importFromPath(path);
    }

    private int upsertFeaturesFromJson(Track track, Path featuresJson) {
        try {
            if (!Files.exists(featuresJson)) {
                throw new BadRequestException("Features JSON not found: " + featuresJson);
            }

            String json = Files.readString(featuresJson);
            List<FeatureItem> items = objectMapper.readValue(json, new TypeReference<>() {});
            int count = 0;

            for (FeatureItem item : items) {
                if (item.key == null || item.key.isBlank()) {
                    continue;
                }

                TrackFeature f = trackFeatureRepository
                        .findByTrackIdAndFeatureKey(track.getId(), item.key)
                        .orElseGet(TrackFeature::new);

                f.setTrack(track);
                f.setFeatureKey(item.key);
                f.setValueString(item.valueString);
                f.setValueNumber(item.valueNumber);
                f.setConfidence(item.confidence);
                f.setSource(FeatureSource.AUDIO_ANALYSIS);
                f.setExtractedAt(Instant.now());

                trackFeatureRepository.save(f);
                count++;
            }

            return count;
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse/upsert features: " + e.getMessage());
        }
    }

    public record AnalyzeResult(int featuresUpserted, String featuresJsonPath) {}
    public record TrainResult(String modelVersion, Map<String, Object> metrics, String metricsJsonPath) {}
    public record MetricsResult(String modelVersion, Map<String, Object> metrics, String metricsJsonPath) {}

    @Data
    private static class FeatureItem {
        public String key;
        public String valueString;
        public Double valueNumber;
        public Double confidence;
    }
}