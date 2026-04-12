package com.example.musicrec.dto.integration.python;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class PythonRecommendResponse {
    @JsonProperty("user_id")
    private String userId;

    private List<String> recommendations;

    private String model;

    public PythonRecommendResponse() {
    }

    public String getUserId() {
        return userId;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public String getModel() {
        return model;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public void setModel(String model) {
        this.model = model;
    }
}