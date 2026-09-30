package com.moviebooking.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "theaters",
        uniqueConstraints = @UniqueConstraint(name = "uk_theater_city_name", columnNames = {"city_id", "name"}),
        indexes = @Index(name = "idx_theater_city", columnList = "city_id"))
@Getter
@Setter
@NoArgsConstructor
public class Theater {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 300)
    private String address;

    public Theater(City city, String name, String address) {
        this.city = city;
        this.name = name;
        this.address = address;
    }
}
