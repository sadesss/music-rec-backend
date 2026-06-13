package com.example.musicrec.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "rating",
        uniqueConstraints = @UniqueConstraint(name = "uq_rating", columnNames = {"user_id", "track_id"})
)
public class Rating extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    /**
     * Rating value on a 10-point scale (1..10).
     * Validation is implemented in RatingService.
     */
    @Column(nullable = false)
    private Integer value;
}
