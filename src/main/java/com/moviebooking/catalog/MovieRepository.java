package com.moviebooking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    /** Movies that have at least one upcoming show in the given city. */
    @Query("select distinct s.movie from Show s " +
            "where s.screen.theater.city.id = :cityId and s.startTime >= :from")
    List<Movie> findPlayingInCity(@Param("cityId") Long cityId, @Param("from") Instant from);
}
