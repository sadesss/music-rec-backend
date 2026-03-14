package com.example.musicrec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "track")
public class Track extends BaseEntity {

    @Column(nullable = false)
    private String title;

    private String artist;
    private String album;

    @Column(name = "original_genre")
    private String originalGenre;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /**
     * Relative key/path inside app.storage.audio-dir.
     * Example: "tracks/<uuid>.mp3"
     */
    @Column(name = "audio_key", nullable = false)
    private String audioKey;

    @Column(name = "audio_original_name")
    private String audioOriginalName;

    @Column(name = "audio_content_type")
    private String audioContentType;

    @Column(name = "audio_size_bytes")
    private Long audioSizeBytes;

    /**
     * Optional metadata (raw JSON or XML text).
     * Kept as TEXT for simplicity.
     */
    @Column(name = "metadata_text", columnDefinition = "text")
    private String metadataText;
}
