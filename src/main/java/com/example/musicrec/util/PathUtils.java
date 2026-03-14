package com.example.musicrec.util;

import com.example.musicrec.config.AppProperties;
import com.example.musicrec.service.artifacts.ArtifactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Small helper to resolve paths using Spring configuration indirectly.
 * In real code you would inject FileStorageService directly into AdminService.
 */
public final class PathUtils {

    private PathUtils() {}

    // Hack-free resolution: we reconstruct audio path using system properties.
    // Better approach: inject FileStorageService into AdminService and call resolveAudioPath().
    public static Path resolveAudioPath(String audioKey, ArtifactService artifactService) {
        // artifactService has access to props, but not to audio dir.
        // For simplicity, read the same property from system env / default:
        String audioDir = System.getProperty("app.storage.audio-dir", "./data/audio");
        return Paths.get(audioDir).toAbsolutePath().normalize().resolve(audioKey).normalize();
    }
}
