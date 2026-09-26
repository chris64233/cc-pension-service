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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务年限版本。每次期间登记或更正生成一个新版本，版本号在参保人维度单调递增。
 * 版本及其分段一旦写入不再修改，供核定锁定引用。
 */
@Entity
@Table(name = "service_year_version",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_version_person_no", columnNames = {"person_id", "version_no"}),
                @UniqueConstraint(name = "uk_version_trigger", columnNames = {"person_id", "trigger_registration_no"})
        })
public class ServiceYearVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false, length = 64)
    private String personId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    /** 触发本版本的登记/更正登记号（幂等键）。 */
    @Column(name = "trigger_registration_no", nullable = false, length = 64)
    private String triggerRegistrationNo;

    @Column(name = "total_credited_years", nullable = false, precision = 14, scale = 4)
    private BigDecimal totalCreditedYears;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq")
    private List<MergedSegment> segments = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ServiceYearVersion() {
    }

    public ServiceYearVersion(String personId, int versionNo, String triggerRegistrationNo,
                              BigDecimal totalCreditedYears) {
        this.personId = personId;
        this.versionNo = versionNo;
        this.triggerRegistrationNo = triggerRegistrationNo;
        this.totalCreditedYears = totalCreditedYears;
        this.createdAt = Instant.now();
    }

    public void addSegment(MergedSegment segment) {
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

    public String getTriggerRegistrationNo() {
        return triggerRegistrationNo;
    }

    public BigDecimal getTotalCreditedYears() {
        return totalCreditedYears;
    }

    public List<MergedSegment> getSegments() {
        return segments;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
