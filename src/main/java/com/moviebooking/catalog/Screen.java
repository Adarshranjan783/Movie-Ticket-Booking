package com.moviebooking.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "screens",
        uniqueConstraints = @UniqueConstraint(name = "uk_screen_theater_name", columnNames = {"theater_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @Column(nullable = false, length = 50)
    private String name;

    public Screen(Theater theater, String name) {
        this.theater = theater;
        this.name = name;
    }
}
