package com.example.musicrec.service.admin;

import com.example.musicrec.config.AppProperties;
import com.example.musicrec.domain.Track;
import com.example.musicrec.dto.admin.JamendoImportRequest;
import com.example.musicrec.dto.admin.JamendoImportResponse;
import com.example.musicrec.exception.BadRequestException;
import com.example.musicrec.exception.StorageException;
import com.example.musicrec.repository.TrackRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JamendoBulkImportService {

    private final TrackRepository trackRepository;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public JamendoImportResponse importMoodthemeSubset(JamendoImportRequest request) {
        Path datasetRoot = Paths.get(request.datasetRoot()).toAbsolutePath().normalize();
        Path audioRoot = Paths.get(request.audioRoot()).toAbsolutePath().normalize();

        Path subsetTsv = datasetRoot.resolve("data").resolve("autotagging_moodtheme.tsv");
        Path rawMetaTsv = datasetRoot.resolve("data").resolve("raw.meta.tsv");

        if (!Files.exists(subsetTsv)) {
            throw new BadRequestException("Subset metadata file not found: " + subsetTsv);
        }
        if (!Files.exists(rawMetaTsv)) {
            throw new BadRequestException("Raw meta file not found: " + rawMetaTsv);
        }
        if (!Files.exists(audioRoot) || !Files.isDirectory(audioRoot)) {
            throw new BadRequestException("Audio root not found: " + audioRoot);
        }

        Path storageBase = Paths.get(appProperties.getStorage().getAudioDir()).toAbsolutePath().normalize();
        Path targetTracksDir = storageBase.resolve("tracks");

        try {
            Files.createDirectories(targetTracksDir);
        } catch (IOException e) {
            throw new StorageException("Failed to create target directory: " + targetTracksDir, e);
        }

        Map<String, RawMetaRow> rawMetaMap = readRawMeta(rawMetaTsv);
        List<SubsetRow> subsetRows = readSubset(subsetTsv);

        int imported = 0;
        int skipped = 0;
        List<String> importedIds = new ArrayList<>();

        for (SubsetRow row : subsetRows) {
            if (imported >= request.limit()) {
                break;
            }

            Path relativeAudioPath = Paths.get(row.path());
            String fileName = relativeAudioPath.getFileName().toString();

            // Для audio-low заменяем .mp3 -> .low.mp3
            String lowName = fileName.endsWith(".mp3")
                    ? fileName.substring(0, fileName.length() - 4) + ".low.mp3"
                    : fileName;

            Path lowRelativePath = relativeAudioPath.getParent() == null
                    ? Paths.get(lowName)
                    : relativeAudioPath.getParent().resolve(lowName);

            Path sourceMp3 = audioRoot.resolve(lowRelativePath).normalize();

            if (!Files.exists(sourceMp3)) {
                skipped++;
                continue;
            }

            if (trackRepository.existsByAudioOriginalName(lowName)) {
                skipped++;
                continue;
            }

            RawMetaRow meta = rawMetaMap.get(row.trackId());

            UUID trackUuid = UUID.randomUUID();
            String storedName = trackUuid + ".mp3";
            Path targetFile = targetTracksDir.resolve(storedName);

            try {
                Files.copy(sourceMp3, targetFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new StorageException("Failed to copy file " + sourceMp3 + " to " + targetFile, e);
            }

            Track track = new Track();
            track.setId(trackUuid);
            track.setTitle(nonBlank(meta != null ? meta.title() : null, "Jamendo Track " + row.trackId()));
            track.setArtist(meta != null ? blankToNull(meta.artistName()) : null);
            track.setAlbum(meta != null ? blankToNull(meta.albumName()) : null);
            track.setOriginalGenre(null);
            track.setDurationSeconds(parseDurationSeconds(row.duration()));
            track.setAudioKey("tracks/" + storedName);
            track.setAudioOriginalName(lowName);
            track.setAudioContentType("audio/mpeg");

            try {
                track.setAudioSizeBytes(Files.size(sourceMp3));
            } catch (IOException e) {
                track.setAudioSizeBytes(null);
            }

            track.setMetadataText(buildMetadataJson(row, meta, sourceMp3));
            trackRepository.save(track);

            imported++;
            importedIds.add(track.getId().toString());
        }

        return new JamendoImportResponse(
                datasetRoot.toString(),
                audioRoot.toString(),
                request.limit(),
                imported,
                skipped,
                importedIds
        );
    }

    private Map<String, RawMetaRow> readRawMeta(Path rawMetaTsv) {
        Map<String, RawMetaRow> map = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(rawMetaTsv)) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                throw new BadRequestException("Empty TSV: " + rawMetaTsv);
            }

            List<String> headers = splitTsv(headerLine);
            Map<String, Integer> idx = indexMap(headers);

            String trackIdCol = requireAny(idx, "track_id", "TRACK_ID", "id");
            String artistCol = firstExisting(idx, "artist_name", "ARTIST_NAME", "artist");
            String albumCol = firstExisting(idx, "album_name", "ALBUM_NAME", "album");
            String titleCol = firstExisting(idx, "track_name", "TRACK_NAME", "title", "track_title");
            String releaseDateCol = firstExisting(idx, "release_date", "RELEASE_DATE");
            String urlCol = firstExisting(idx, "track_url", "TRACK_URL", "url");

            String line;
            while ((line = br.readLine()) != null) {
                List<String> values = splitTsv(line);

                String trackId = get(values, idx.get(trackIdCol));
                if (trackId == null || trackId.isBlank()) {
                    continue;
                }

                map.put(trackId, new RawMetaRow(
                        trackId,
                        getOptional(values, idx, artistCol),
                        getOptional(values, idx, albumCol),
                        getOptional(values, idx, titleCol),
                        getOptional(values, idx, releaseDateCol),
                        getOptional(values, idx, urlCol)
                ));
            }

            return map;
        } catch (IOException e) {
            throw new StorageException("Failed to read raw.meta.tsv: " + rawMetaTsv, e);
        }
    }

    private List<SubsetRow> readSubset(Path subsetTsv) {
        List<SubsetRow> rows = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(subsetTsv)) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                throw new BadRequestException("Empty TSV: " + subsetTsv);
            }

            List<String> headers = splitTsv(headerLine);
            Map<String, Integer> idx = indexMap(headers);

            String trackIdCol = requireAny(idx, "track_id", "TRACK_ID", "id");
            String pathCol = requireAny(idx, "path", "PATH");
            String durationCol = firstExisting(idx, "duration", "DURATION");
            String tagsCol = firstExisting(idx, "tags", "TAGS");

            String line;
            while ((line = br.readLine()) != null) {
                List<String> values = splitTsv(line);

                String trackId = get(values, idx.get(trackIdCol));
                String path = get(values, idx.get(pathCol));

                if (trackId == null || trackId.isBlank() || path == null || path.isBlank()) {
                    continue;
                }

                rows.add(new SubsetRow(
                        trackId,
                        path,
                        getOptional(values, idx, durationCol),
                        parseTags(getOptional(values, idx, tagsCol))
                ));
            }

            return rows;
        } catch (IOException e) {
            throw new StorageException("Failed to read subset TSV: " + subsetTsv, e);
        }
    }

    private String buildMetadataJson(SubsetRow row, RawMetaRow meta, Path sourceMp3) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "jamendo-bulk-import");
            payload.put("jamendoTrackId", row.trackId());
            payload.put("originalDatasetPath", row.path());
            payload.put("originalAudioPath", sourceMp3.toString());
            payload.put("tags", row.tags());
            payload.put("importedAt", Instant.now().toString());

            if (meta != null) {
                payload.put("artistName", meta.artistName());
                payload.put("albumName", meta.albumName());
                payload.put("trackTitle", meta.title());
                payload.put("releaseDate", meta.releaseDate());
                payload.put("trackUrl", meta.trackUrl());
            }

            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"source\":\"jamendo-bulk-import\"}";
        }
    }

    private Integer parseDurationSeconds(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    int idx = s.indexOf("---");
                    return idx >= 0 ? s.substring(idx + 3) : s;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Integer> indexMap(List<String> headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            map.put(headers.get(i), i);
        }
        return map;
    }

    private String requireAny(Map<String, Integer> idx, String... names) {
        for (String name : names) {
            if (idx.containsKey(name)) {
                return name;
            }
        }
        throw new BadRequestException("Required TSV column not found. Tried: " + Arrays.toString(names));
    }

    private String firstExisting(Map<String, Integer> idx, String... names) {
        for (String name : names) {
            if (name != null && idx.containsKey(name)) {
                return name;
            }
        }
        return null;
    }

    private List<String> splitTsv(String line) {
        return Arrays.asList(line.split("\t", -1));
    }

    private String get(List<String> values, Integer index) {
        if (index == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private String getOptional(List<String> values, Map<String, Integer> idx, String col) {
        if (col == null) {
            return null;
        }
        return get(values, idx.get(col));
    }

    private String nonBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private record RawMetaRow(
            String trackId,
            String artistName,
            String albumName,
            String title,
            String releaseDate,
            String trackUrl
    ) {}

    private record SubsetRow(
            String trackId,
            String path,
            String duration,
            List<String> tags
    ) {}
}
