package com.chris64233.pensionservice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 待遇核定快照。核定时锁定一个服务年限版本，将累计年限、待遇基数与分段明细
 * 完整拷贝到本表，形成不可修改快照；后续期间更正不影响本记录。
 */
@Entity
@Table(name = "benefit_assessment")
public class BenefitAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false, length = 64)
    private String personId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "total_credited_years", nullable = false, precision = 14, scale = 4)
    private BigDecimal totalCreditedYears;

    @Column(name = "reference_wage", nullable = false, precision = 14, scale = 2)
    private BigDecimal referenceWage;

    @Column(name = "accrual_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal accrualRate;

    @Column(name = "benefit_base", nullable = false, precision = 14, scale = 2)
    private BigDecimal benefitBase;

    @Column(name = "monthly_benefit", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyBenefit;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq")
    private List<AssessmentSegment> segments = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BenefitAssessment() {
    }

    public BenefitAssessment(String personId, int versionNo, BigDecimal totalCreditedYears,
                             BigDecimal referenceWage, BigDecimal accrualRate,
                             BigDecimal benefitBase, BigDecimal monthlyBenefit) {
        this.personId = personId;
        this.versionNo = versionNo;
        this.totalCreditedYears = totalCreditedYears;
        this.referenceWage = referenceWage;
        this.accrualRate = accrualRate;
        this.benefitBase = benefitBase;
        this.monthlyBenefit = monthlyBenefit;
        this.createdAt = Instant.now();
    }

    public void addSegment(AssessmentSegment segment) {
        segments.add(segment);
    }

    public Long getId() {
        return id;
    }

    public String getPersonId() {
        return personId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public BigDecimal getTotalCreditedYears() {
        return totalCreditedYears;
    }

    public BigDecimal getReferenceWage() {
        return referenceWage;
    }

    public BigDecimal getAccrualRate() {
        return accrualRate;
    }

    public BigDecimal getBenefitBase() {
        return benefitBase;
    }

    public BigDecimal getMonthlyBenefit() {
        return monthlyBenefit;
    }

    public List<AssessmentSegment> getSegments() {
        return segments;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
