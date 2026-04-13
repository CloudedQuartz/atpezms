package com.atpezms.atpezms.ticketing.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import com.atpezms.atpezms.common.entity.SeasonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * The explicit price matrix for a {@link PassType}.
 *
 * <p>Instead of hardcoding pricing formulas (e.g. "child is 50% of adult"),
 * the system uses a flat table of explicit prices for every combination of
 * {@link AgeGroup}, {@link DayType}, and {@link SeasonType}.
 *
 * <h2>Rationale (from DESIGN.md)</h2>
 * <ul>
 *   <li>Easy to explain to auditors and staff.</li>
 *   <li>Easy to modify via an admin UI (in a future slice) without code changes.</li>
 *   <li>Avoids subtle bugs and rounding errors from complex formulas.</li>
 * </ul>
 *
 * <p>During ticket issuance, the ticketing service finds the single matching
 * price row and copies its {@code priceCents} and {@code currency} into the
 * new {@code Ticket} as a snapshot.
 */
@Entity
@Table(name = "pass_type_prices")
public class PassTypePrice extends BaseEntity {

    /**
     * The pass type this price applies to.
     * We use a @ManyToOne relationship because this is an intra-context
     * relationship (both entities live in the ticketing context).
     *
     * <p>FetchType.LAZY is generally preferred, but since we always need the
     * PassType when loading its prices, the default EAGER is acceptable here
     * (and keeps things simple for the educational focus).
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "pass_type_id", nullable = false)
    private PassType passType;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false, length = 10)
    private AgeGroup ageGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 10)
    private DayType dayType;

    @Enumerated(EnumType.STRING)
    @Column(name = "season_type", nullable = false, length = 10)
    private SeasonType seasonType;

    /**
     * The price in cents (e.g. 150000 = 1500.00).
     * Using an integer avoids floating-point precision issues with money.
     */
    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    protected PassTypePrice() {
        // For JPA
    }

    public PassTypePrice(PassType passType, AgeGroup ageGroup, DayType dayType,
                         SeasonType seasonType, int priceCents, String currency) {
        if (passType == null) {
            throw new IllegalArgumentException("passType is required");
        }
        if (ageGroup == null) {
            throw new IllegalArgumentException("ageGroup is required");
        }
        if (dayType == null) {
            throw new IllegalArgumentException("dayType is required");
        }
        if (seasonType == null) {
            throw new IllegalArgumentException("seasonType is required");
        }
        if (priceCents <= 0) {
            throw new IllegalArgumentException("priceCents must be > 0");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        this.passType = passType;
        this.ageGroup = ageGroup;
        this.dayType = dayType;
        this.seasonType = seasonType;
        this.priceCents = priceCents;
        this.currency = currency;
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (passType == null) {
            throw new IllegalStateException("passType is required");
        }
        if (ageGroup == null) {
            throw new IllegalStateException("ageGroup is required");
        }
        if (dayType == null) {
            throw new IllegalStateException("dayType is required");
        }
        if (seasonType == null) {
            throw new IllegalStateException("seasonType is required");
        }
        if (priceCents <= 0) {
            throw new IllegalStateException("priceCents must be > 0");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalStateException("currency is required");
        }
    }

    public PassType getPassType() {
        return passType;
    }

    public AgeGroup getAgeGroup() {
        return ageGroup;
    }

    public DayType getDayType() {
        return dayType;
    }

    public SeasonType getSeasonType() {
        return seasonType;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public String getCurrency() {
        return currency;
    }
}
