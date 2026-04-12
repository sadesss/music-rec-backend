package com.example.musicrec.dto.integration.python;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PythonRecommendRequest {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("top_k")
    private Integer topK;

    @JsonProperty("filter_seen")
    private Boolean filterSeen;

    public PythonRecommendRequest() {
    }

    public PythonRecommendRequest(String userId, Integer topK, Boolean filterSeen) {
        this.userId = userId;
        this.topK = topK;
        this.filterSeen = filterSeen;
    }

    public String getUserId() {
        return userId;
    }

    public Integer getTopK() {
        return topK;
    }

    public Boolean getFilterSeen() {
        return filterSeen;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public void setFilterSeen(Boolean filterSeen) {
        this.filterSeen = filterSeen;
    }
}