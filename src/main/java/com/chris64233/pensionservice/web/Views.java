package com.chris64233.pensionservice.web;

import com.chris64233.pensionservice.domain.AssessmentSegment;
import com.chris64233.pensionservice.domain.BenefitAdjustment;
import com.chris64233.pensionservice.domain.BenefitAssessment;
import com.chris64233.pensionservice.domain.MergedSegment;
import com.chris64233.pensionservice.domain.ServicePeriod;
import com.chris64233.pensionservice.domain.ServiceYearVersion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 查询与命令响应视图。 */
public final class Views {

    private Views() {
    }

    public record SegmentView(LocalDate startDate, LocalDate endDate, BigDecimal ratio, BigDecimal creditedYears) {
        static SegmentView of(MergedSegment s) {
            return new SegmentView(s.getStartDate(), s.getEndDate(), s.getRatio(), s.getCreditedYears());
        }

        static SegmentView of(AssessmentSegment s) {
            return new SegmentView(s.getStartDate(), s.getEndDate(), s.getRatio(), s.getCreditedYears());
        }
    }

    public record PeriodView(Long id, String registrationNo, String employer, LocalDate startDate, LocalDate endDate,
                             BigDecimal creditRatio, boolean voided, boolean superseded,
                             String correctsRegistrationNo, Instant createdAt) {
        public static PeriodView of(ServicePeriod p) {
            return new PeriodView(p.getId(), p.getRegistrationNo(), p.getEmployer(), p.getStartDate(), p.getEndDate(),
                    p.getCreditRatio(), p.isVoided(), p.isSuperseded(), p.getCorrectsRegistrationNo(), p.getCreatedAt());
        }
    }

    public record VersionView(int versionNo, String triggerRegistrationNo, BigDecimal totalCreditedYears,
                              List<SegmentView> segments, Instant createdAt) {
        public static VersionView of(ServiceYearVersion v) {
            return new VersionView(v.getVersionNo(), v.getTriggerRegistrationNo(), v.getTotalCreditedYears(),
                    v.getSegments().stream().map(SegmentView::of).toList(), v.getCreatedAt());
        }
    }

    public record AssessmentView(Long id, int versionNo, BigDecimal totalCreditedYears, BigDecimal referenceWage,
                                 BigDecimal accrualRate, BigDecimal benefitBase, BigDecimal monthlyBenefit,
                                 List<SegmentView> segments, Instant createdAt) {
        public static AssessmentView of(BenefitAssessment a) {
            return new AssessmentView(a.getId(), a.getVersionNo(), a.getTotalCreditedYears(), a.getReferenceWage(),
                    a.getAccrualRate(), a.getBenefitBase(), a.getMonthlyBenefit(),
                    a.getSegments().stream().map(SegmentView::of).toList(), a.getCreatedAt());
        }
    }

    public record AdjustmentView(Long id, Long assessmentId, int fromVersionNo, int toVersionNo,
                                 BigDecimal oldMonthlyBenefit, BigDecimal newMonthlyBenefit, BigDecimal difference,
                                 String direction, Instant createdAt) {
        public static AdjustmentView of(BenefitAdjustment a) {
            return new AdjustmentView(a.getId(), a.getAssessmentId(), a.getFromVersionNo(), a.getToVersionNo(),
                    a.getOldMonthlyBenefit(), a.getNewMonthlyBenefit(), a.getDifference(),
                    a.getDirection().name(), a.getCreatedAt());
        }
    }
}
