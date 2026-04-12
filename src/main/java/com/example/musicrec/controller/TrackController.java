package com.example.musicrec.controller;

import com.example.musicrec.domain.Track;
import com.example.musicrec.dto.track.TrackResponse;
import com.example.musicrec.service.TrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tracks")
public class TrackController {

    private final TrackService trackService;

    @GetMapping
    public List<TrackResponse> list() {
        return trackService.listLatest().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public TrackResponse get(@PathVariable UUID id) {
        Track t = trackService.get(id);
        return toResponse(t);
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<Resource> stream(@PathVariable UUID id) {
        Resource res = trackService.streamAudio(id);
        String contentType = trackService.audioContentType(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(res);
    }

    private TrackResponse toResponse(Track t) {
        return TrackResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .artist(t.getArtist())
                .album(t.getAlbum())
                .originalGenre(t.getOriginalGenre())
                .durationSeconds(t.getDurationSeconds())
                .audioUrl("/api/v1/tracks/" + t.getId() + "/stream")
                .features(trackService.featuresAsMap(t.getId()))
                .createdAt(t.getCreatedAt())
                .build();
    }
}