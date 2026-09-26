package com.chris64233.pensionservice.web;

import com.chris64233.pensionservice.service.AdjustmentService;
import com.chris64233.pensionservice.service.AssessmentService;
import com.chris64233.pensionservice.service.PensionQueryService;
import com.chris64233.pensionservice.service.PeriodService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/persons/{personId}")
public class PensionController {

    private final PeriodService periodService;
    private final AssessmentService assessmentService;
    private final AdjustmentService adjustmentService;
    private final PensionQueryService queryService;

    public PensionController(PeriodService periodService,
                             AssessmentService assessmentService,
                             AdjustmentService adjustmentService,
                             PensionQueryService queryService) {
        this.periodService = periodService;
        this.assessmentService = assessmentService;
        this.adjustmentService = adjustmentService;
        this.queryService = queryService;
    }

    public record RegisterPeriodRequest(
            @NotBlank String registrationNo,
            @NotBlank String employer,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @DecimalMin("0.0001") @DecimalMax("1") BigDecimal creditRatio) {
    }

    public record CorrectionRequest(
            @NotBlank String registrationNo,
            @NotBlank String correctsRegistrationNo,
            String employer,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal creditRatio,
            boolean voided) {
    }

    public record AssessmentRequest(@NotNull @DecimalMin("0.01") BigDecimal referenceWage) {
    }

    public record AdjustmentRequest(@NotNull Long assessmentId,
                                    @NotNull @DecimalMin("0.01") BigDecimal referenceWage) {
    }

    @PostMapping("/periods")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.VersionView registerPeriod(@PathVariable String personId,
                                            @Valid @RequestBody RegisterPeriodRequest request) {
        return Views.VersionView.of(periodService.registerPeriod(personId, request.registrationNo(),
                request.employer(), request.startDate(), request.endDate(), request.creditRatio()));
    }

    @PostMapping("/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.VersionView correctPeriod(@PathVariable String personId,
                                           @Valid @RequestBody CorrectionRequest request) {
        return Views.VersionView.of(periodService.correctPeriod(personId, request.registrationNo(),
                request.correctsRegistrationNo(), request.employer(), request.startDate(), request.endDate(),
                request.creditRatio(), request.voided()));
    }

    @PostMapping("/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.AssessmentView assess(@PathVariable String personId,
                                       @Valid @RequestBody AssessmentRequest request) {
        return Views.AssessmentView.of(assessmentService.assess(personId, request.referenceWage()));
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.AdjustmentView createAdjustment(@PathVariable String personId,
                                                 @Valid @RequestBody AdjustmentRequest request) {
        return Views.AdjustmentView.of(adjustmentService.createAdjustment(personId, request.assessmentId(),
                request.referenceWage()));
    }

    @GetMapping("/periods")
    public List<Views.PeriodView> listPeriods(@PathVariable String personId) {
        return queryService.listPeriods(personId);
    }

    @GetMapping("/versions")
    public List<Views.VersionView> listVersions(@PathVariable String personId) {
        return queryService.listVersions(personId);
    }

    @GetMapping("/versions/{versionNo}")
    public Views.VersionView getVersion(@PathVariable String personId, @PathVariable int versionNo) {
        return queryService.getVersion(personId, versionNo);
    }

    @GetMapping("/assessments")
    public List<Views.AssessmentView> listAssessments(@PathVariable String personId) {
        return queryService.listAssessments(personId);
    }

    @GetMapping("/assessments/{assessmentId}")
    public Views.AssessmentView getAssessment(@PathVariable String personId, @PathVariable long assessmentId) {
        return queryService.getAssessment(personId, assessmentId);
    }

    @GetMapping("/adjustments")
    public List<Views.AdjustmentView> listAdjustments(@PathVariable String personId) {
        return queryService.listAdjustments(personId);
    }
}
