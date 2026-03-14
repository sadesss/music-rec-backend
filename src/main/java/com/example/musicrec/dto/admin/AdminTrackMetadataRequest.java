package com.example.musicrec.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminTrackMetadataRequest {

    @NotBlank
    private String title;

    private String artist;
    private String album;
    private String genre;
    private Integer durationSeconds;

    /**
     * Optional raw metadata text (json/xml).
     */
    private String metadataText;
}
