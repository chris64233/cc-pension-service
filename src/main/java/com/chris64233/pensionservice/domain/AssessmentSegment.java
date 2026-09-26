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

/** 核定快照中的分段明细，自版本拷贝，独立于后续版本变化。 */
@Entity
@Table(name = "assessment_segment")
public class AssessmentSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private BenefitAssessment assessment;

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

    protected AssessmentSegment() {
    }

    public AssessmentSegment(BenefitAssessment assessment, int seq, LocalDate startDate, LocalDate endDate,
                             BigDecimal ratio, BigDecimal creditedYears) {
        this.assessment = assessment;
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
