package com.example.musicrec.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Storage storage = new Storage();
    private Artifacts artifacts = new Artifacts();
    private Python python = new Python();
    private Security security = new Security();
    private Cors cors = new Cors();

    @Data
    public static class Storage {
        /**
         * Directory to store uploaded mp3 files.
         * Adapt for your desktop environment.
         */
        private String audioDir = "./data/audio";
    }

    @Data
    public static class Artifacts {
        /**
         * Directory to store JSON artifacts produced by offline Python scripts.
         * Example:
         *  - features/<trackId>.json
         *  - model/model.json
         *  - model/metrics.json
         *  - training/training_data.jsonl
         */
        private String dir = "./data/artifacts";
    }

    @Data
    public static class Python {
        /**
         * Python executable (python3 on Linux/macOS, py/python on Windows).
         */
        private String executable = "python3";

        /**
         * Directory where Python scripts live (relative or absolute).
         */
        private String scriptsDir = "./python";

        /**
         * Script names (resolved relative to scriptsDir).
         */
        private String analyzeScript = "analyze_track.py";
        private String trainScript = "train_model.py";

        /**
         * Max allowed runtime for python scripts.
         */
        private long timeoutSeconds = 600;
    }

    @Data
    public static class Security {
        /**
         * Placeholder protection for admin endpoints:
         * if enabled == true, /api/admin/** requires X-Admin-Token header.
         */
        private boolean adminTokenEnabled = false;

        /**
         * Configure via env var ADMIN_TOKEN ideally.
         */
        private String adminToken = "change_me";
    }

    @Data
    public static class Cors {
        /**
         * Allowed origins list. Use "*" for dev or set explicit allow-list.
         */
        private List<String> allowedOrigins = List.of("*");
    }
}
