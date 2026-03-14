package com.example.musicrec.controller;

import com.example.musicrec.domain.Track;
import com.example.musicrec.dto.admin.*;
import com.example.musicrec.service.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.musicrec.dto.admin.JamendoImportRequest;
import com.example.musicrec.dto.admin.JamendoImportResponse;
import com.example.musicrec.service.admin.JamendoBulkImportService;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1")
public class AdminController {
    private final JamendoBulkImportService jamendoBulkImportService;

    private final AdminService adminService;

    @PostMapping(value = "/tracks/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminUploadTrackResponse uploadTrack(@RequestPart("file") MultipartFile file,
                                                @RequestPart("metadata") @Valid AdminTrackMetadataRequest metadata) {

        Track t = adminService.uploadTrack(metadata, file);
        return AdminUploadTrackResponse.builder()
                .trackId(t.getId())
                .audioKey(t.getAudioKey())
                .message("Uploaded successfully")
                .build();
    }

    @PostMapping("/import/jamendo")
    public JamendoImportResponse importJamendo(@Valid @RequestBody JamendoImportRequest request) {
        return jamendoBulkImportService.importMoodthemeSubset(request);
    }


    @PostMapping("/tracks/{trackId}/analyze")
    public AdminAnalyzeResponse analyze(@PathVariable java.util.UUID trackId) {
        var res = adminService.analyzeTrack(trackId);
        return AdminAnalyzeResponse.builder()
                .trackId(trackId)
                .featuresUpserted(res.featuresUpserted())
                .featuresJsonPath(res.featuresJsonPath())
                .build();
    }

    @PostMapping("/train")
    public TrainResponse train(@RequestBody(required = false) TrainRequest req) {
        String notes = req == null ? "" : req.getNotes();
        var res = adminService.trainModel(notes);
        return TrainResponse.builder()
                .modelVersion(res.modelVersion())
                .metrics(res.metrics())
                .metricsJsonPath(res.metricsJsonPath())
                .build();
    }

    @GetMapping("/metrics")
    public MetricsResponse metrics() {
        var res = adminService.readMetrics();
        return MetricsResponse.builder()
                .modelVersion(res.modelVersion())
                .metrics(res.metrics())
                .metricsJsonPath(res.metricsJsonPath())
                .build();
    }

    @PostMapping("/recommendations/import")
    public java.util.Map<String, Object> importRecommendations(@Valid @RequestBody ImportRecommendationsRequest req) {
        int imported = adminService.importRecommendations(req.getPath());
        return java.util.Map.of("imported", imported);
    }
}
