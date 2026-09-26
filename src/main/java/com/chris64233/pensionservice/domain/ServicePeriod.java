package com.chris64233.pensionservice.domain;

import com.chris64233.pensionservice.service.ServiceYearsCalculator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 任职期间。登记号 (person_id, registration_no) 唯一，作为幂等键。
 * 期间采用版本化保存：versionIntroduced 记录生效版本，
 * 被更正时不删除，仅标记 supersededByVersion，历史版本可完整回放。
 */
@Entity
@Table(name = "service_period", uniqueConstraints =
        @UniqueConstraint(name = "uk_period_reg_no", columnNames = {"person_id", "registration_no"}))
public class ServicePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private InsuredPerson person;

    @Column(name = "registration_no", nullable = false, length = 64)
    private String registrationNo;

    @Column(nullable = false, length = 128)
    private String unit;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 计入比例，(0, 1]，1 表示全额计入。 */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal ratio;

    @Column(name = "version_introduced", nullable = false)
    private int versionIntroduced;

    /** 被更正取代的版本号；为 null 表示当前有效。 */
    @Column(name = "superseded_by_version")
    private Integer supersededByVersion;

    /** 更正产生的期间指向被更正的原期间。 */
    @Column(name = "corrects_period_id")
    private Long correctsPeriodId;

    protected ServicePeriod() {
    }

    public ServicePeriod(InsuredPerson person, String registrationNo, String unit,
                         LocalDate startDate, LocalDate endDate, BigDecimal ratio,
                         int versionIntroduced, Long correctsPeriodId) {
        this.person = person;
        this.registrationNo = registrationNo;
        this.unit = unit;
        this.startDate = startDate;
        this.endDate = endDate;
        this.ratio = ratio;
        this.versionIntroduced = versionIntroduced;
        this.correctsPeriodId = correctsPeriodId;
    }

    /** 幂等判断：同一登记号重复登记且内容完全一致时视为同一笔。 */
    public boolean matches(String unit, LocalDate startDate, LocalDate endDate, BigDecimal ratio) {
        return this.unit.equals(unit)
                && this.startDate.equals(startDate)
                && this.endDate.equals(endDate)
                && this.ratio.compareTo(ratio) == 0;
    }

    public ServiceYearsCalculator.PeriodSlice toSlice() {
        return new ServiceYearsCalculator.PeriodSlice(startDate, endDate, ratio);
    }

    public Long getId() {
        return id;
    }

    public InsuredPerson getPerson() {
        return person;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public String getUnit() {
        return unit;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getRatio() {
        return ratio;
    }

    public int getVersionIntroduced() {
        return versionIntroduced;
    }

    public Integer getSupersededByVersion() {
        return supersededByVersion;
    }

    public void setSupersededByVersion(Integer supersededByVersion) {
        this.supersededByVersion = supersededByVersion;
    }

    public Long getCorrectsPeriodId() {
        return correctsPeriodId;
    }

    public boolean isCurrent() {
        return supersededByVersion == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServicePeriod that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
