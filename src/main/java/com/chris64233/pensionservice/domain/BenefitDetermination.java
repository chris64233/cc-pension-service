package com.chris64233.pensionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 待遇核定快照。生成时锁定一个服务年限版本，记录累计年限、
 * 待遇基数与计算过程明细，生成后不可修改（无更新入口，仅新增）。
 */
@Entity
@Table(name = "benefit_determination")
public class BenefitDetermination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private InsuredPerson person;

    /** 核定锁定的服务年限版本。 */
    @Column(name = "version_used", nullable = false)
    private int versionUsed;

    /** 累计服务年限（年，4 位小数）。 */
    @Column(name = "total_years", nullable = false, precision = 12, scale = 4)
    private BigDecimal totalYears;

    /** 待遇基数（元，2 位小数）。 */
    @Column(name = "benefit_base", nullable = false, precision = 14, scale = 2)
    private BigDecimal benefitBase;

    /** 月待遇额 = 待遇基数 × 累计年限 × 1%。 */
    @Column(name = "monthly_benefit", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyBenefit;

    /** 计算过程明细（分段折算结果）的 JSON 快照。 */
    @Lob
    @Column(name = "snapshot_json", nullable = false)
    private String snapshotJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BenefitDetermination() {
    }

    public BenefitDetermination(InsuredPerson person, int versionUsed, BigDecimal totalYears,
                                BigDecimal benefitBase, BigDecimal monthlyBenefit, String snapshotJson) {
        this.person = person;
        this.versionUsed = versionUsed;
        this.totalYears = totalYears;
        this.benefitBase = benefitBase;
        this.monthlyBenefit = monthlyBenefit;
        this.snapshotJson = snapshotJson;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public InsuredPerson getPerson() {
        return person;
    }

    public int getVersionUsed() {
        return versionUsed;
    }

    public BigDecimal getTotalYears() {
        return totalYears;
    }

    public BigDecimal getBenefitBase() {
        return benefitBase;
    }

    public BigDecimal getMonthlyBenefit() {
        return monthlyBenefit;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
