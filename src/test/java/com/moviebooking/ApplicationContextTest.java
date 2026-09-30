package com.moviebooking;

import com.moviebooking.catalog.CityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApplicationContextTest {

    @Autowired
    CityRepository cities;

    @Test
    void contextLoadsAndPersistenceWorks() {
        assertThat(cities.count()).isGreaterThanOrEqualTo(0);
    }
}
