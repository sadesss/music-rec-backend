package com.example.musicrec.dto.admin;

import lombok.Data;

import java.util.Map;

@Data
public class ModelSettingsRequest {
    /**
     * Demonstration parameters selected in the administrator panel.
     * The values are stored in memory and are used by mock training/metrics endpoints.
     */
    private Map<String, Object> settings;
}
