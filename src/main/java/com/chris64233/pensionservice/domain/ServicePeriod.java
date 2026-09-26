package com.chris64233.pensionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 任职期间登记记录。登记号在参保人维度唯一，用于幂等。
 * 更正不修改原记录：原记录被标记 superseded，更正内容作为新记录插入（作废更正 voided=true）。
 */
@Entity
@Table(name = "service_period",
        uniqueConstraints = @UniqueConstraint(name = "uk_period_registration", columnNames = {"person_id", "registration_no"}))
public class ServicePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false, length = 64)
    private String personId;

    @Column(name = "registration_no", nullable = false, length = 64)
    private String registrationNo;

    @Column(nullable = false, length = 128)
    private String employer;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 计入比例，(0, 1]，1 表示全额计入。 */
    @Column(name = "credit_ratio", nullable = false, precision = 5, scale = 4)
    private BigDecimal creditRatio;

    /** 作废更正：该记录仅作审计，不参与年限计算。 */
    @Column(nullable = false)
    private boolean voided;

    /** 已被后续更正取代。 */
    @Column(nullable = false)
    private boolean superseded;

    @Column(name = "corrects_registration_no", length = 64)
    private String correctsRegistrationNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ServicePeriod() {
    }

    public ServicePeriod(String personId, String registrationNo, String employer,
                         LocalDate startDate, LocalDate endDate, BigDecimal creditRatio,
                         boolean voided, String correctsRegistrationNo) {
        this.personId = personId;
        this.registrationNo = registrationNo;
        this.employer = employer;
        this.startDate = startDate;
        this.endDate = endDate;
        this.creditRatio = creditRatio;
        this.voided = voided;
        this.superseded = false;
        this.correctsRegistrationNo = correctsRegistrationNo;
        this.createdAt = Instant.now();
    }

    public void markSuperseded() {
        this.superseded = true;
    }

    public Long getId() {
        return id;
    }

    public String getPersonId() {
        return personId;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public String getEmployer() {
        return employer;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getCreditRatio() {
        return creditRatio;
    }

    public boolean isVoided() {
        return voided;
    }

    public boolean isSuperseded() {
        return superseded;
    }

    public String getCorrectsRegistrationNo() {
        return correctsRegistrationNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
