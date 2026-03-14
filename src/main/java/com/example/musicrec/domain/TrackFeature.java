package com.example.musicrec.domain;

import com.example.musicrec.domain.enums.FeatureSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
        name = "track_feature",
        uniqueConstraints = @UniqueConstraint(name = "uq_track_feature", columnNames = {"track_id", "feature_key"})
)
public class TrackFeature extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @Column(name = "feature_key", nullable = false)
    private String featureKey;

    @Column(name = "value_string")
    private String valueString;

    @Column(name = "value_number")
    private Double valueNumber;

    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureSource source = FeatureSource.AUDIO_ANALYSIS;

    @Column(name = "extracted_at", nullable = false)
    private Instant extractedAt = Instant.now();
}
