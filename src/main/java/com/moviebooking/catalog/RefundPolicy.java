package com.moviebooking.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configurable cancellation rules. Refund percentage depends on how many hours remain before the show:
 * <pre>
 *   hoursBeforeShow >= fullRefundHoursBefore     -> 100%
 *   hoursBeforeShow >= partialRefundHoursBefore  -> partialRefundPercent
 *   otherwise                                     -> 0%
 * </pre>
 */
@Entity
@Table(name = "refund_policies",
        uniqueConstraints = @UniqueConstraint(name = "uk_refund_policy_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
public class RefundPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int fullRefundHoursBefore;

    @Column(nullable = false)
    private int partialRefundHoursBefore;

    @Column(nullable = false)
    private int partialRefundPercent;

    /** Exactly one policy should be the default; used by shows without an explicit policy. */
    @Column(nullable = false)
    private boolean defaultPolicy;

    public RefundPolicy(String name, int fullRefundHoursBefore, int partialRefundHoursBefore,
                        int partialRefundPercent, boolean defaultPolicy) {
        this.name = name;
        this.fullRefundHoursBefore = fullRefundHoursBefore;
        this.partialRefundHoursBefore = partialRefundHoursBefore;
        this.partialRefundPercent = partialRefundPercent;
        this.defaultPolicy = defaultPolicy;
    }

    public int refundPercentFor(long hoursBeforeShow) {
        if (hoursBeforeShow >= fullRefundHoursBefore) return 100;
        if (hoursBeforeShow >= partialRefundHoursBefore) return partialRefundPercent;
        return 0;
    }
}
