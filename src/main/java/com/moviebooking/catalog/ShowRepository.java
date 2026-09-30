package com.moviebooking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShowRepository extends JpaRepository<Show, Long> {

    @Query("select s from Show s join fetch s.movie join fetch s.screen sc join fetch sc.theater t join fetch t.city " +
            "where s.id = :id")
    Optional<Show> findByIdWithDetails(@Param("id") Long id);

    /** True if any show on the same screen overlaps the [start, end) window. */
    @Query("select count(s) > 0 from Show s where s.screen.id = :screenId " +
            "and s.startTime < :end and s.endTime > :start")
    boolean existsOverlapping(@Param("screenId") Long screenId,
                              @Param("start") Instant start, @Param("end") Instant end);

    @Query("select s from Show s join fetch s.movie m join fetch s.screen sc join fetch sc.theater t join fetch t.city c " +
            "where c.id = :cityId " +
            "and (:movieId is null or m.id = :movieId) " +
            "and s.startTime >= :from and s.startTime < :to " +
            "order by s.startTime")
    List<Show> search(@Param("cityId") Long cityId, @Param("movieId") Long movieId,
                      @Param("from") Instant from, @Param("to") Instant to);

    /** Upcoming shows starting within a window; used by the reminder job. */
    List<Show> findByStartTimeBetween(Instant from, Instant to);
}
