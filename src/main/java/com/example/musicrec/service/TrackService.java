package com.example.musicrec.service;

import com.example.musicrec.domain.Track;
import com.example.musicrec.domain.TrackFeature;
import com.example.musicrec.dto.admin.AdminTrackMetadataRequest;
import com.example.musicrec.exception.BadRequestException;
import com.example.musicrec.exception.NotFoundException;
import com.example.musicrec.repository.TrackFeatureRepository;
import com.example.musicrec.repository.TrackRepository;
import com.example.musicrec.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrackService {

    private final TrackRepository trackRepository;
    private final TrackFeatureRepository trackFeatureRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public Track createFromUpload(AdminTrackMetadataRequest metadata, MultipartFile mp3) {
        if (mp3 == null || mp3.isEmpty()) {
            throw new BadRequestException("MP3 file is required");
        }

        Track t = new Track();
        t.setId(UUID.randomUUID()); // UUID нужен заранее, чтобы по нему сохранить файл
        t.setTitle(metadata.getTitle());
        t.setArtist(metadata.getArtist());
        t.setAlbum(metadata.getAlbum());
        t.setOriginalGenre(metadata.getGenre());
        t.setDurationSeconds(metadata.getDurationSeconds());
        t.setMetadataText(metadata.getMetadataText());

        String audioKey = null;
        try {
            audioKey = fileStorageService.storeMp3(t.getId(), mp3);

            t.setAudioKey(audioKey);
            t.setAudioOriginalName(mp3.getOriginalFilename());
            t.setAudioContentType(
                    mp3.getContentType() != null && !mp3.getContentType().isBlank()
                            ? mp3.getContentType()
                            : "audio/mpeg"
            );
            t.setAudioSizeBytes(mp3.getSize());

            return trackRepository.save(t);
        } catch (Exception e) {
            if (audioKey != null) {
                fileStorageService.deleteIfExists(audioKey);
            }
            throw e;
        }
    }

    public Track get(UUID trackId) {
        return trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("Track not found: " + trackId));
    }

    public List<Track> listLatest() {
        return trackRepository.findTop50ByOrderByCreatedAtDesc();
    }

    public Map<String, Object> featuresAsMap(UUID trackId) {
        List<TrackFeature> features = trackFeatureRepository.findByTrackId(trackId);
        Map<String, Object> map = new LinkedHashMap<>();
        for (TrackFeature f : features) {
            Object val = f.getValueNumber() != null ? f.getValueNumber() : f.getValueString();
            map.put(f.getFeatureKey(), val);
        }
        return map;
    }

    public Resource streamAudio(UUID trackId) {
        Track t = get(trackId);
        return fileStorageService.loadAsResource(t.getAudioKey());
    }

    public String audioContentType(UUID trackId) {
        Track t = get(trackId);
        return t.getAudioContentType() != null ? t.getAudioContentType() : "audio/mpeg";
    }
}