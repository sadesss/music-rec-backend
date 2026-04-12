package com.example.musicrec.service.storage;

import com.example.musicrec.config.AppProperties;
import com.example.musicrec.exception.StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final AppProperties props;

    public String storeMp3(UUID trackId, MultipartFile multipartFile) {
        String originalName = StringUtils.cleanPath(
                multipartFile.getOriginalFilename() == null ? "track.mp3" : multipartFile.getOriginalFilename()
        );

        if (!originalName.toLowerCase().endsWith(".mp3")) {
            throw new StorageException("Only .mp3 files are supported");
        }

        Path baseDir = Paths.get(props.getStorage().getAudioDir()).toAbsolutePath().normalize();
        Path tracksDir = baseDir.resolve("tracks");

        try {
            Files.createDirectories(tracksDir);

            String storedName = trackId + ".mp3";
            Path target = tracksDir.resolve(storedName);

            Files.copy(multipartFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            return "tracks/" + storedName;
        } catch (IOException e) {
            throw new StorageException("Failed to store mp3: " + e.getMessage(), e);
        }
    }

    public void deleteIfExists(String audioKey) {
        try {
            Path path = resolveAudioPath(audioKey);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new StorageException("Failed to delete audio file: " + e.getMessage(), e);
        }
    }

    public Path resolveAudioPath(String audioKey) {
        Path baseDir = Paths.get(props.getStorage().getAudioDir()).toAbsolutePath().normalize();
        return baseDir.resolve(audioKey).normalize();
    }

    public Resource loadAsResource(String audioKey) {
        Path filePath = resolveAudioPath(audioKey);
        if (!Files.exists(filePath)) {
            throw new StorageException("Audio file not found: " + filePath);
        }
        return new FileSystemResource(filePath);
    }
}