package com.chris64233.pensionservice.service;

import com.chris64233.pensionservice.domain.AdjustmentDirection;
import com.chris64233.pensionservice.domain.BenefitAdjustment;
import com.chris64233.pensionservice.domain.BenefitAssessment;
import com.chris64233.pensionservice.repo.BenefitAdjustmentRepository;
import com.chris64233.pensionservice.repo.BenefitAssessmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class AssessmentAdjustmentTest {

    private static final String PERSON = "p-assess";
    private static final BigDecimal ONE = new BigDecimal("1");
    private static final BigDecimal WAGE = new BigDecimal("10000");

    @Autowired
    PeriodService periodService;
    @Autowired
    AssessmentService assessmentService;
    @Autowired
    AdjustmentService adjustmentService;
    @Autowired
    BenefitAssessmentRepository assessments;
    @Autowired
    BenefitAdjustmentRepository adjustments;

    private void registerBasePeriod() {
        periodService.registerPeriod(PERSON, "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE);
    }

    @Test
    void assessmentLocksVersionAndComputesBenefit() {
        registerBasePeriod();
        BenefitAssessment a = assessmentService.assess(PERSON, WAGE);
        assertEquals(1, a.getVersionNo());
        assertEquals(new BigDecimal("10.0082"), a.getTotalCreditedYears());
        assertEquals(new BigDecimal("0.1001"), a.getAccrualRate());
        assertEquals(new BigDecimal("1001.00"), a.getBenefitBase());
        assertEquals(new BigDecimal("1001.00"), a.getMonthlyBenefit());
        assertEquals(1, a.getSegments().size());
    }

    @Test
    void correctionAfterAssessmentLeavesSnapshotUntouched() {
        registerBasePeriod();
        BenefitAssessment a = assessmentService.assess(PERSON, WAGE);
        periodService.correctPeriod(PERSON, "C1", "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2014, 12, 31), ONE, false);
        BenefitAssessment reloaded = assessments.findByIdAndPersonId(a.getId(), PERSON).orElseThrow();
        assertEquals(1, reloaded.getVersionNo());
        assertEquals(new BigDecimal("10.0082"), reloaded.getTotalCreditedYears());
        assertEquals(new BigDecimal("1001.00"), reloaded.getMonthlyBenefit());
    }

    @Test
    void supplementAdjustmentIsRecordedInLedger() {
        registerBasePeriod();
        BenefitAssessment a = assessmentService.assess(PERSON, WAGE);
        periodService.correctPeriod(PERSON, "C1", "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2014, 12, 31), ONE, false);
        BenefitAdjustment adj = adjustmentService.createAdjustment(PERSON, a.getId(), WAGE);
        assertEquals(1, adj.getFromVersionNo());
        assertEquals(2, adj.getToVersionNo());
        assertEquals(new BigDecimal("1001.00"), adj.getOldMonthlyBenefit());
        assertEquals(new BigDecimal("1501.00"), adj.getNewMonthlyBenefit());
        assertEquals(new BigDecimal("500.00"), adj.getDifference());
        assertEquals(AdjustmentDirection.SUPPLEMENT, adj.getDirection());
        // 原核定不被覆盖
        assertEquals(new BigDecimal("1001.00"),
                assessments.findByIdAndPersonId(a.getId(), PERSON).orElseThrow().getMonthlyBenefit());
    }

    @Test
    void recoveryAdjustmentIsRecordedInLedger() {
        registerBasePeriod();
        BenefitAssessment a = assessmentService.assess(PERSON, WAGE);
        periodService.correctPeriod(PERSON, "C1", "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2004, 12, 31), ONE, false);
        BenefitAdjustment adj = adjustmentService.createAdjustment(PERSON, a.getId(), WAGE);
        assertEquals(new BigDecimal("501.00"), adj.getNewMonthlyBenefit());
        assertEquals(new BigDecimal("-500.00"), adj.getDifference());
        assertEquals(AdjustmentDirection.RECOVERY, adj.getDirection());
    }

    @Test
    void adjustmentChainAccumulatesAcrossCorrections() {
        registerBasePeriod();
        BenefitAssessment a = assessmentService.assess(PERSON, WAGE);
        periodService.correctPeriod(PERSON, "C1", "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2014, 12, 31), ONE, false);
        adjustmentService.createAdjustment(PERSON, a.getId(), WAGE);
        periodService.correctPeriod(PERSON, "C2", "C1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2019, 12, 31), ONE, false);
        adjustmentService.createAdjustment(PERSON, a.getId(), WAGE);

        List<BenefitAdjustment> chain = adjustments.findByPersonIdOrderByIdAsc(PERSON);
        assertEquals(2, chain.size());
        assertEquals(2, chain.get(0).getToVersionNo());
        assertEquals(3, chain.get(1).getToVersionNo());
        // 第二次更正后累计 20.0137 年 → 计发比例 0.2001 → 月待遇 2001.00
        assertEquals(new BigDecimal("2001.00"), chain.get(1).getNewMonthlyBenefit());
        assertEquals(new BigDecimal("1000.00"), chain.get(1).getDifference());
    }
}
