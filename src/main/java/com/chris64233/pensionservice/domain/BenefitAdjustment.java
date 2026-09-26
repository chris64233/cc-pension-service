package com.chris64233.pensionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 待遇差额台账。期间更正产生新版本后，基于最新版本重算待遇并与原核定比较，
 * 差额（正=补发，负=追回）形成独立台账记录，不覆盖原核定。
 */
@Entity
@Table(name = "benefit_adjustment")
public class BenefitAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false, length = 64)
    private String personId;

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(name = "from_version_no", nullable = false)
    private int fromVersionNo;

    @Column(name = "to_version_no", nullable = false)
    private int toVersionNo;

    @Column(name = "old_monthly_benefit", nullable = false, precision = 14, scale = 2)
    private BigDecimal oldMonthlyBenefit;

    @Column(name = "new_monthly_benefit", nullable = false, precision = 14, scale = 2)
    private BigDecimal newMonthlyBenefit;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal difference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AdjustmentDirection direction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BenefitAdjustment() {
    }

    public BenefitAdjustment(String personId, Long assessmentId, int fromVersionNo, int toVersionNo,
                             BigDecimal oldMonthlyBenefit, BigDecimal newMonthlyBenefit) {
        this.personId = personId;
        this.assessmentId = assessmentId;
        this.fromVersionNo = fromVersionNo;
        this.toVersionNo = toVersionNo;
        this.oldMonthlyBenefit = oldMonthlyBenefit;
        this.newMonthlyBenefit = newMonthlyBenefit;
        this.difference = newMonthlyBenefit.subtract(oldMonthlyBenefit);
        this.direction = switch (this.difference.signum()) {
            case 1 -> AdjustmentDirection.SUPPLEMENT;
            case -1 -> AdjustmentDirection.RECOVERY;
            default -> AdjustmentDirection.NONE;
        };
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPersonId() {
        return personId;
    }

    public Long getAssessmentId() {
        return assessmentId;
    }

    public int getFromVersionNo() {
        return fromVersionNo;
    }

    public int getToVersionNo() {
        return toVersionNo;
    }

    public BigDecimal getOldMonthlyBenefit() {
        return oldMonthlyBenefit;
    }

    public BigDecimal getNewMonthlyBenefit() {
        return newMonthlyBenefit;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public AdjustmentDirection getDirection() {
        return direction;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
