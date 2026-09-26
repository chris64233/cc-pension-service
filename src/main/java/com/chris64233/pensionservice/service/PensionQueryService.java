package com.chris64233.pensionservice.service;

import com.chris64233.pensionservice.repo.BenefitAdjustmentRepository;
import com.chris64233.pensionservice.repo.BenefitAssessmentRepository;
import com.chris64233.pensionservice.repo.ServicePeriodRepository;
import com.chris64233.pensionservice.repo.ServiceYearVersionRepository;
import com.chris64233.pensionservice.web.Views;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 期间、版本、核定快照与差额链的只读查询。 */
@Service
@Transactional(readOnly = true)
public class PensionQueryService {

    private final ServicePeriodRepository periods;
    private final ServiceYearVersionRepository versions;
    private final BenefitAssessmentRepository assessments;
    private final BenefitAdjustmentRepository adjustments;

    public PensionQueryService(ServicePeriodRepository periods,
                               ServiceYearVersionRepository versions,
                               BenefitAssessmentRepository assessments,
                               BenefitAdjustmentRepository adjustments) {
        this.periods = periods;
        this.versions = versions;
        this.assessments = assessments;
        this.adjustments = adjustments;
    }

    public List<Views.PeriodView> listPeriods(String personId) {
        return periods.findByPersonIdOrderByIdAsc(personId).stream().map(Views.PeriodView::of).toList();
    }

    public List<Views.VersionView> listVersions(String personId) {
        return versions.findByPersonIdOrderByVersionNoAsc(personId).stream().map(Views.VersionView::of).toList();
    }

    public Views.VersionView getVersion(String personId, int versionNo) {
        return versions.findByPersonIdAndVersionNo(personId, versionNo)
                .map(Views.VersionView::of)
                .orElseThrow(() -> ApiException.notFound("版本不存在: " + versionNo));
    }

    public List<Views.AssessmentView> listAssessments(String personId) {
        return assessments.findByPersonIdOrderByIdAsc(personId).stream().map(Views.AssessmentView::of).toList();
    }

    public Views.AssessmentView getAssessment(String personId, long assessmentId) {
        return assessments.findByIdAndPersonId(assessmentId, personId)
                .map(Views.AssessmentView::of)
                .orElseThrow(() -> ApiException.notFound("核定记录不存在: " + assessmentId));
    }

    public List<Views.AdjustmentView> listAdjustments(String personId) {
        return adjustments.findByPersonIdOrderByIdAsc(personId).stream().map(Views.AdjustmentView::of).toList();
    }
}
