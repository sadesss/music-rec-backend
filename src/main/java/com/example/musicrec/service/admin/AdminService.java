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

    /**
     * In-memory state for demonstration endpoints used by the administrator panel.
     * These settings are intentionally not persisted because the panel emulates ML operations.
     */
    private volatile Map<String, Object> modelSettings = defaultModelSettings();
    private volatile Instant modelSettingsUpdatedAt = Instant.now();
    private volatile String demoModelVersion = "NewSASRec-demo";

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

    public ModelSettingsResult getModelSettings() {
        return new ModelSettingsResult(new LinkedHashMap<>(modelSettings), modelSettingsUpdatedAt);
    }

    public ModelSettingsResult saveModelSettings(Map<String, Object> settings) {
        modelSettings = normalizeModelSettings(settings);
        modelSettingsUpdatedAt = Instant.now();
        return getModelSettings();
    }

    public TrainResult trainModelMock(String notes, Map<String, Object> settings) {
        if (settings != null && !settings.isEmpty()) {
            saveModelSettings(settings);
        }

        String suffix = String.format("%03d", Instant.now().toEpochMilli() % 1000);
        demoModelVersion = "NewSASRec-demo-" + suffix;

        Map<String, Object> metrics = buildMockMetrics(modelSettings);
        metrics.put("notes", notes == null ? "" : notes);
        return new TrainResult(demoModelVersion, metrics, "mock://admin-panel/metrics.json");
    }

    public MetricsResult calculateMockMetrics(Map<String, Object> settings) {
        if (settings != null && !settings.isEmpty()) {
            saveModelSettings(settings);
        }

        Map<String, Object> metrics = buildMockMetrics(modelSettings);
        return new MetricsResult(demoModelVersion, metrics, "mock://admin-panel/metrics.json");
    }

    public int importRecommendations(String path) {
        return recommendationImportService.importFromPath(path);
    }

    private Map<String, Object> defaultModelSettings() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("epochs", 50);
        defaults.put("batchSize", 512);
        defaults.put("learningRate", 0.0003);
        defaults.put("historyLength", 50);
        defaults.put("candidates", 1000);
        defaults.put("topK", 10);
        defaults.put("easeL2", 500);
        defaults.put("dropout", 0.2);
        defaults.put("filterSeen", true);
        defaults.put("diversityBoost", true);
        defaults.put("novelMode", false);
        return defaults;
    }

    private Map<String, Object> normalizeModelSettings(Map<String, Object> input) {
        Map<String, Object> defaults = defaultModelSettings();
        Map<String, Object> normalized = new LinkedHashMap<>(defaults);
        Map<String, Object> source = input == null ? Map.of() : input;

        normalized.put("epochs", clampInt(asDouble(source, "epochs", asDouble(defaults, "epochs", 50)), 5, 100));
        normalized.put("batchSize", clampInt(asDouble(source, "batchSize", asDouble(defaults, "batchSize", 512)), 128, 2048));
        normalized.put("learningRate", round4(clamp(asDouble(source, "learningRate", asDouble(defaults, "learningRate", 0.0003)), 0.0001, 0.0100)));
        normalized.put("historyLength", clampInt(asDouble(source, "historyLength", asDouble(defaults, "historyLength", 50)), 10, 120));
        normalized.put("candidates", clampInt(asDouble(source, "candidates", asDouble(defaults, "candidates", 1000)), 100, 5000));
        normalized.put("topK", clampInt(asDouble(source, "topK", asDouble(defaults, "topK", 10)), 5, 100));
        normalized.put("easeL2", clampInt(asDouble(source, "easeL2", asDouble(defaults, "easeL2", 500)), 50, 1000));
        normalized.put("dropout", round4(clamp(asDouble(source, "dropout", asDouble(defaults, "dropout", 0.2)), 0.0, 0.6)));
        normalized.put("filterSeen", asBoolean(source, "filterSeen", asBoolean(defaults, "filterSeen", true)));
        normalized.put("diversityBoost", asBoolean(source, "diversityBoost", asBoolean(defaults, "diversityBoost", true)));
        normalized.put("novelMode", asBoolean(source, "novelMode", asBoolean(defaults, "novelMode", false)));

        return normalized;
    }

    private Map<String, Object> buildMockMetrics(Map<String, Object> settings) {
        Map<String, Object> source = normalizeModelSettings(settings);

        double epochs = asDouble(source, "epochs", 50);
        double candidates = asDouble(source, "candidates", 1000);
        double historyLength = asDouble(source, "historyLength", 50);
        double learningRate = asDouble(source, "learningRate", 0.0003);
        double easeL2 = asDouble(source, "easeL2", 500);
        double dropout = asDouble(source, "dropout", 0.2);
        boolean filterSeen = asBoolean(source, "filterSeen", true);
        boolean diversityBoostEnabled = asBoolean(source, "diversityBoost", true);
        boolean novelMode = asBoolean(source, "novelMode", false);

        double epochsFactor = clamp(epochs / 50.0, 0.35, 1.35);
        double candidateFactor = clamp(Math.sqrt(candidates / 1000.0), 0.35, 2.25);
        double historyFactor = clamp(historyLength / 50.0, 0.45, 1.5);
        double lrPenalty = clamp(Math.abs(learningRate - 0.0003) * 22.0, 0.0, 0.11);
        double dropoutPenalty = dropout > 0.35 ? 0.018 : 0.0;
        double regularizationBonus = 1.0 - Math.min(Math.abs(easeL2 - 500.0) / 1200.0, 0.18);
        double quality = clamp(0.72 + epochsFactor * 0.12 + historyFactor * 0.07 + regularizationBonus * 0.06 - lrPenalty - dropoutPenalty, 0.55, 0.98);
        double novelPenalty = novelMode ? 0.05 : 0.0;
        double filterBoost = filterSeen ? 0.012 : 0.0;
        double diversityBoost = diversityBoostEnabled ? 0.022 : 0.0;

        Map<String, Object> metrics = new LinkedHashMap<>();
        double precision = clamp(0.028 + quality * 0.014 + candidateFactor * 0.003 - novelPenalty * 0.09, 0.018, 0.055);
        double recall = clamp(0.255 + quality * 0.086 + candidateFactor * 0.018 - novelPenalty, 0.13, 0.42);
        double hitRate = clamp(recall + 0.006 + filterBoost, 0.14, 0.45);
        double ndcg = clamp(0.178 + quality * 0.076 + historyFactor * 0.015 - novelPenalty * 0.8, 0.08, 0.31);
        double diversity = clamp(0.112 + diversityBoost + (filterSeen ? 0.012 : 0.0) + Math.min(candidates / 5000.0, 1.0) * 0.008, 0.08, 0.18);
        double serendipity = clamp(0.245 + quality * 0.065 + diversityBoost + (novelMode ? 0.018 : 0.0), 0.13, 0.37);

        metrics.put("Precision@10", round4(precision));
        metrics.put("Recall@10", round4(recall));
        metrics.put("HitRate@10", round4(hitRate));
        metrics.put("NDCG@10", round4(ndcg));
        metrics.put("Diversity@100", round4(diversity));
        metrics.put("Serendipity@100", round4(serendipity));
        return metrics;
    }

    private double asDouble(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean asBoolean(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return fallback;
    }

    private int clampInt(double value, int min, int max) {
        return (int) Math.round(clamp(value, min, max));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
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
    public record ModelSettingsResult(Map<String, Object> settings, Instant updatedAt) {}
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
