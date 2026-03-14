package com.example.musicrec.dto.admin;

import lombok.Data;

@Data
public class TrainRequest {
    /**
     * Free-form notes for your own use. Not persisted; just logged.
     */
    private String notes;
}
