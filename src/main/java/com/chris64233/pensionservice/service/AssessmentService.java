package com.chris64233.pensionservice.service;

import com.chris64233.pensionservice.domain.AssessmentSegment;
import com.chris64233.pensionservice.domain.BenefitAssessment;
import com.chris64233.pensionservice.domain.BenefitCalculator;
import com.chris64233.pensionservice.domain.MergedSegment;
import com.chris64233.pensionservice.domain.ServiceYearVersion;
import com.chris64233.pensionservice.repo.BenefitAssessmentRepository;
import com.chris64233.pensionservice.repo.ServiceYearVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 待遇核定。在参保人锁内读取最新服务年限版本并生成不可修改快照，
 * 与并发的期间更正互斥，核定永远基于一个完整版本。
 */
@Service
public class AssessmentService {

    private final PeriodService periodService;
    private final ServiceYearVersionRepository versions;
    private final BenefitAssessmentRepository assessments;

    public AssessmentService(PeriodService periodService,
                             ServiceYearVersionRepository versions,
                             BenefitAssessmentRepository assessments) {
        this.periodService = periodService;
        this.versions = versions;
        this.assessments = assessments;
    }

    @Transactional
    public BenefitAssessment assess(String personId, BigDecimal referenceWage) {
        if (referenceWage == null || referenceWage.signum() <= 0) {
            throw ApiException.badRequest("参考工资必须为正数");
        }
        periodService.lockPerson(personId);
        ServiceYearVersion version = versions.findTopByPersonIdOrderByVersionNoDesc(personId)
                .orElseThrow(() -> ApiException.conflict("参保人尚无服务年限版本，无法核定: " + personId));
        BenefitCalculator.Benefit benefit = BenefitCalculator.calculate(referenceWage, version.getTotalCreditedYears());
        BenefitAssessment assessment = new BenefitAssessment(personId, version.getVersionNo(),
                version.getTotalCreditedYears(), referenceWage,
                benefit.accrualRate(), benefit.benefitBase(), benefit.monthlyBenefit());
        for (MergedSegment s : version.getSegments()) {
            assessment.addSegment(new AssessmentSegment(assessment, s.getSeq(), s.getStartDate(), s.getEndDate(),
                    s.getRatio(), s.getCreditedYears()));
        }
        return assessments.save(assessment);
    }
}
