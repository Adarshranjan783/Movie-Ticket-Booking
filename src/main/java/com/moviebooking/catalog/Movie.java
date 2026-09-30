package com.moviebooking.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(length = 50)
    private String language;

    @Column(length = 50)
    private String genre;

    public Movie(String title, int durationMinutes, String language, String genre) {
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.language = language;
        this.genre = genre;
    }
}
