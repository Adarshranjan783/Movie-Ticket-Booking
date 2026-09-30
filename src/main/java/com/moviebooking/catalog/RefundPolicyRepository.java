package com.moviebooking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {
    Optional<RefundPolicy> findFirstByDefaultPolicyTrue();
}
