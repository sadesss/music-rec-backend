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
     * Assumption: value can encode like/dislike (-1/1) or star rating (1..5).
     * Validation is implemented in RatingService.
     */
    @Column(nullable = false)
    private Integer value;
}
