package com.example.musicrec.dto.admin;

import lombok.Data;

import java.util.Map;

@Data
public class TrainRequest {
    /**
     * Free-form notes for your own use. Not persisted; just logged.
     */
    private String notes;

    /**
     * Optional demonstration model settings from the administrator panel.
     */
    private Map<String, Object> settings;
}
