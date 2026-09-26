package com.chris64233.pensionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 版本内的合并分段（重叠处理后的结果）。 */
@Entity
@Table(name = "merged_segment")
public class MergedSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private ServiceYearVersion version;

    @Column(nullable = false)
    private int seq;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal ratio;

    @Column(name = "credited_years", nullable = false, precision = 14, scale = 4)
    private BigDecimal creditedYears;

    protected MergedSegment() {
    }

    public MergedSegment(ServiceYearVersion version, int seq, LocalDate startDate, LocalDate endDate,
                         BigDecimal ratio, BigDecimal creditedYears) {
        this.version = version;
        this.seq = seq;
        this.startDate = startDate;
        this.endDate = endDate;
        this.ratio = ratio;
        this.creditedYears = creditedYears;
    }

    public Long getId() {
        return id;
    }

    public int getSeq() {
        return seq;
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

    public BigDecimal getCreditedYears() {
        return creditedYears;
    }
}
