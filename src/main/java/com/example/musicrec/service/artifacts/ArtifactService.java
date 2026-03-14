package com.example.musicrec.service.artifacts;

import com.example.musicrec.config.AppProperties;
import com.example.musicrec.exception.StorageException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArtifactService {

    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public Path artifactsBaseDir() {
        return Paths.get(props.getArtifacts().getDir()).toAbsolutePath().normalize();
    }

    public Path featuresDir() {
        return artifactsBaseDir().resolve("features");
    }

    public Path trainingDir() {
        return artifactsBaseDir().resolve("training");
    }

    public Path modelDir() {
        return artifactsBaseDir().resolve("model");
    }

    public Path featuresJsonPath(UUID trackId) {
        return featuresDir().resolve(trackId + ".json");
    }

    public Path trainingDataPath() {
        return trainingDir().resolve("training_data.jsonl");
    }

    public Path metricsPath() {
        return modelDir().resolve("metrics.json");
    }

    public Path modelPath() {
        return modelDir().resolve("model.json");
    }

    public void ensureDirs() {
        try {
            Files.createDirectories(featuresDir());
            Files.createDirectories(trainingDir());
            Files.createDirectories(modelDir());
        } catch (IOException e) {
            throw new StorageException("Failed to create artifacts directories: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> readMetricsOrEmpty() {
        Path p = metricsPath();
        if (!Files.exists(p)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(Files.readString(p), new TypeReference<>() {});
        } catch (IOException e) {
            throw new StorageException("Failed to read metrics.json: " + e.getMessage(), e);
        }
    }

    public String readModelVersionOrUnknown() {
        Path p = modelPath();
        if (!Files.exists(p)) {
            return "unknown";
        }
        try {
            Map<String, Object> model = objectMapper.readValue(Files.readString(p), new TypeReference<>() {});
            Object v = model.get("modelVersion");
            return v == null ? "unknown" : String.valueOf(v);
        } catch (IOException e) {
            return "unknown";
        }
    }

    public Instant lastModified(Path path) {
        try {
            if (!Files.exists(path)) return null;
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return null;
        }
    }
}
