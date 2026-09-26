package com.chris64233.pensionservice.web;

import com.chris64233.pensionservice.domain.BenefitDetermination;
import com.chris64233.pensionservice.service.PensionService;
import com.chris64233.pensionservice.service.ServiceYearsCalculator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/persons")
public class PensionController {

    private final PensionService service;

    public PensionController(PensionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.PersonResponse createPerson(@Valid @RequestBody Dtos.CreatePersonRequest req) {
        return Dtos.PersonResponse.from(service.registerPerson(req.personNo(), req.name()));
    }

    /** 登记任职期间（登记号幂等）。 */
    @PostMapping("/{personId}/periods")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.PeriodResponse registerPeriod(@PathVariable Long personId,
                                              @Valid @RequestBody Dtos.PeriodRequest req) {
        return Dtos.PeriodResponse.from(service.registerPeriod(personId, req.registrationNo(),
                req.unit(), req.startDate(), req.endDate(), req.ratio()));
    }

    /** 更正历史期间，产生新版本。 */
    @PostMapping("/{personId}/periods/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.PeriodResponse correctPeriod(@PathVariable Long personId,
                                             @Valid @RequestBody Dtos.CorrectionRequest req) {
        return Dtos.PeriodResponse.from(service.correctPeriod(personId, req.originalRegistrationNo(),
                req.registrationNo(), req.unit(), req.startDate(), req.endDate(), req.ratio()));
    }

    /** 期间版本列表：每个版本引入/取代的期间。 */
    @GetMapping("/{personId}/versions")
    public List<Dtos.VersionResponse> listVersions(@PathVariable Long personId) {
        return service.listVersions(personId).stream()
                .map(v -> new Dtos.VersionResponse(v.version(),
                        v.introduced().stream().map(Dtos.PeriodResponse::from).toList(),
                        v.superseded().stream().map(Dtos.PeriodResponse::from).toList()))
                .toList();
    }

    /** 指定版本下有效的期间集合。 */
    @GetMapping("/{personId}/versions/{version}/periods")
    public List<Dtos.PeriodResponse> periodsAtVersion(@PathVariable Long personId,
                                                      @PathVariable int version) {
        return service.periodsAtVersion(personId, version).stream()
                .map(Dtos.PeriodResponse::from)
                .toList();
    }

    /** 指定版本的重叠处理与折算明细。 */
    @GetMapping("/{personId}/versions/{version}/overlap")
    public Dtos.OverlapResponse overlapAtVersion(@PathVariable Long personId,
                                                 @PathVariable int version) {
        ServiceYearsCalculator.Calculation calc = service.overlapAtVersion(personId, version);
        return new Dtos.OverlapResponse(version, calc.totalYears(),
                calc.segments().stream().map(Dtos.SegmentResponse::from).toList());
    }

    /** 生成待遇核定：锁定当前版本并写入不可变快照。 */
    @PostMapping("/{personId}/determinations")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.DeterminationResponse determine(@PathVariable Long personId,
                                                @Valid @RequestBody Dtos.DetermineRequest req) {
        BenefitDetermination d = service.determine(personId, req.averageWage());
        return Dtos.DeterminationResponse.from(d, service.snapshotOf(d));
    }

    /** 核定快照列表。 */
    @GetMapping("/{personId}/determinations")
    public List<Dtos.DeterminationResponse> listDeterminations(@PathVariable Long personId) {
        return service.listDeterminations(personId).stream()
                .map(d -> Dtos.DeterminationResponse.from(d, service.snapshotOf(d)))
                .toList();
    }

    /** 差额链：新旧核定差额台账。 */
    @GetMapping("/{personId}/adjustments")
    public List<Dtos.AdjustmentResponse> listAdjustments(@PathVariable Long personId) {
        return service.listAdjustments(personId).stream()
                .map(Dtos.AdjustmentResponse::from)
                .toList();
    }
}
