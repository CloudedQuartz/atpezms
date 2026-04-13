package com.atpezms.atpezms.ticketing.entity;

import com.atpezms.atpezms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A purchase record for park admission.
 *
 * <p>A ticket is immutable once created. It captures the price paid at the time
 * of purchase as a snapshot ({@code pricePaidCents}), so if the underlying
 * {@link PassTypePrice} changes tomorrow, this ticket's history remains accurate.
 *
 * <p>The validity window ({@code validFrom} to {@code validTo}) is computed at
 * issuance based on the {@link PassType}'s multi-day rules. For a single-day
 * pass, both dates are equal to {@code visitDate}.
 */
@Entity
@Table(name = "tickets")
public class Ticket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visitor_id", nullable = false)
    private Visitor visitor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pass_type_id", nullable = false)
    private PassType passType;

    /** The anchor date the visitor selected for their visit. */
    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    /** Computed start of validity (inclusive). Usually equal to visitDate. */
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** Computed end of validity (inclusive). */
    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    /** Price snapshot. */
    @Column(name = "price_paid_cents", nullable = false)
    private int pricePaidCents;

    /** Currency snapshot. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * The exact moment the transaction occurred.
     * While BaseEntity provides createdAt, purchasedAt is explicit domain data
     * (the timestamp on the receipt).
     */
    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    protected Ticket() {
        // For JPA
    }

    /**
     * Creates a new Ticket.
     *
     * <p>For a single-day pass, {@code validFrom} and {@code validTo} must equal
     * {@code visitDate}. For a multi-day pass, {@code validTo} will be later.
     */
    public Ticket(Visitor visitor, PassType passType, LocalDate visitDate,
                  LocalDate validFrom, LocalDate validTo, int pricePaidCents,
                  String currency, Instant purchasedAt) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor is required");
        }
        if (passType == null) {
            throw new IllegalArgumentException("passType is required");
        }
        if (visitDate == null) {
            throw new IllegalArgumentException("visitDate is required");
        }
        if (validFrom == null) {
            throw new IllegalArgumentException("validFrom is required");
        }
        if (validTo == null) {
            throw new IllegalArgumentException("validTo is required");
        }
        if (validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo cannot be before validFrom");
        }
        if (pricePaidCents <= 0) {
            throw new IllegalArgumentException("pricePaidCents must be > 0");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        if (purchasedAt == null) {
            throw new IllegalArgumentException("purchasedAt is required");
        }
        
        this.visitor = visitor;
        this.passType = passType;
        this.visitDate = visitDate;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.pricePaidCents = pricePaidCents;
        this.currency = currency;
        this.purchasedAt = purchasedAt;
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (visitor == null) {
            throw new IllegalStateException("Ticket.visitor is required");
        }
        if (passType == null) {
            throw new IllegalStateException("Ticket.passType is required");
        }
        if (visitDate == null) {
            throw new IllegalStateException("Ticket.visitDate is required");
        }
        if (validFrom == null) {
            throw new IllegalStateException("Ticket.validFrom is required");
        }
        if (validTo == null) {
            throw new IllegalStateException("Ticket.validTo is required");
        }
        if (validTo.isBefore(validFrom)) {
            throw new IllegalStateException("Ticket.validTo cannot be before validFrom");
        }
        if (pricePaidCents <= 0) {
            throw new IllegalStateException("Ticket.pricePaidCents must be > 0");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalStateException("Ticket.currency is required");
        }
        if (purchasedAt == null) {
            throw new IllegalStateException("Ticket.purchasedAt is required");
        }
    }

    public Visitor getVisitor() {
        return visitor;
    }

    public PassType getPassType() {
        return passType;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public int getPricePaidCents() {
        return pricePaidCents;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }
}
