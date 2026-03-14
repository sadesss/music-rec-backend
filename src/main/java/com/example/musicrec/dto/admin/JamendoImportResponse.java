package com.example.musicrec.dto.admin;

import java.util.List;

public record JamendoImportResponse(
        String datasetRoot,
        String audioRoot,
        int requestedLimit,
        int importedCount,
        int skippedCount,
        List<String> importedTrackIds
) {
}
