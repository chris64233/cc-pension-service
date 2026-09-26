package com.chris64233.pensionservice.service;

import com.chris64233.pensionservice.domain.BenefitAdjustment;
import com.chris64233.pensionservice.domain.BenefitAssessment;
import com.chris64233.pensionservice.domain.BenefitCalculator;
import com.chris64233.pensionservice.domain.ServiceYearVersion;
import com.chris64233.pensionservice.repo.BenefitAdjustmentRepository;
import com.chris64233.pensionservice.repo.BenefitAssessmentRepository;
import com.chris64233.pensionservice.repo.ServiceYearVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 差额台账。期间更正产生新版本后，按最新版本重算待遇并与指定核定快照比较，
 * 差额（正=补发，负=追回）落为独立台账记录，原核定不被修改。
 */
@Service
public class AdjustmentService {

    private final PeriodService periodService;
    private final ServiceYearVersionRepository versions;
    private final BenefitAssessmentRepository assessments;
    private final BenefitAdjustmentRepository adjustments;

    public AdjustmentService(PeriodService periodService,
                             ServiceYearVersionRepository versions,
                             BenefitAssessmentRepository assessments,
                             BenefitAdjustmentRepository adjustments) {
        this.periodService = periodService;
        this.versions = versions;
        this.assessments = assessments;
        this.adjustments = adjustments;
    }

    @Transactional
    public BenefitAdjustment createAdjustment(String personId, Long assessmentId, BigDecimal referenceWage) {
        if (referenceWage == null || referenceWage.signum() <= 0) {
            throw ApiException.badRequest("参考工资必须为正数");
        }
        periodService.lockPerson(personId);
        BenefitAssessment assessment = assessments.findByIdAndPersonId(assessmentId, personId)
                .orElseThrow(() -> ApiException.notFound("核定记录不存在: " + assessmentId));
        ServiceYearVersion latest = versions.findTopByPersonIdOrderByVersionNoDesc(personId)
                .orElseThrow(() -> ApiException.conflict("参保人尚无服务年限版本: " + personId));
        BenefitCalculator.Benefit benefit = BenefitCalculator.calculate(referenceWage, latest.getTotalCreditedYears());
        return adjustments.save(new BenefitAdjustment(personId, assessment.getId(),
                assessment.getVersionNo(), latest.getVersionNo(),
                assessment.getMonthlyBenefit(), benefit.monthlyBenefit()));
    }
}
