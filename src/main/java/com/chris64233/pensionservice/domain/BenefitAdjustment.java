package com.chris64233.pensionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 待遇差额台账。更正产生新版本并重新核定后，记录新旧核定的月待遇差额，
 * 形成独立的、按时间链式排列的台账，不覆盖任何历史核定。
 */
@Entity
@Table(name = "benefit_adjustment")
public class BenefitAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private InsuredPerson person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "previous_determination_id", nullable = false)
    private BenefitDetermination previousDetermination;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_determination_id", nullable = false)
    private BenefitDetermination newDetermination;

    /** 月待遇差额 = 新核定月待遇 - 原核定月待遇；正为补发，负为追回。 */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal difference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AdjustmentType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BenefitAdjustment() {
    }

    public BenefitAdjustment(InsuredPerson person, BenefitDetermination previousDetermination,
                             BenefitDetermination newDetermination, BigDecimal difference,
                             AdjustmentType type) {
        this.person = person;
        this.previousDetermination = previousDetermination;
        this.newDetermination = newDetermination;
        this.difference = difference;
        this.type = type;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public InsuredPerson getPerson() {
        return person;
    }

    public BenefitDetermination getPreviousDetermination() {
        return previousDetermination;
    }

    public BenefitDetermination getNewDetermination() {
        return newDetermination;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public AdjustmentType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
