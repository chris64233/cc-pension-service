package com.chris64233.pensionservice.web;

import com.chris64233.pensionservice.domain.AdjustmentType;
import com.chris64233.pensionservice.domain.BenefitAdjustment;
import com.chris64233.pensionservice.domain.BenefitDetermination;
import com.chris64233.pensionservice.domain.InsuredPerson;
import com.chris64233.pensionservice.domain.ServicePeriod;
import com.chris64233.pensionservice.service.DeterminationSnapshot;
import com.chris64233.pensionservice.service.ServiceYearsCalculator;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Web 层请求/响应对象。 */
public final class Dtos {

    private Dtos() {
    }

    public record CreatePersonRequest(@NotBlank String personNo, @NotBlank String name) {
    }

    public record PersonResponse(Long id, String personNo, String name, int currentVersion) {
        static PersonResponse from(InsuredPerson p) {
            return new PersonResponse(p.getId(), p.getPersonNo(), p.getName(), p.getCurrentVersion());
        }
    }

    public record PeriodRequest(@NotBlank String registrationNo,
                                @NotBlank String unit,
                                @NotNull LocalDate startDate,
                                @NotNull LocalDate endDate,
                                @NotNull @DecimalMin("0.0001") @DecimalMax("1.0000") BigDecimal ratio) {
    }

    public record CorrectionRequest(@NotBlank String originalRegistrationNo,
                                    @NotBlank String registrationNo,
                                    @NotBlank String unit,
                                    @NotNull LocalDate startDate,
                                    @NotNull LocalDate endDate,
                                    @NotNull @DecimalMin("0.0001") @DecimalMax("1.0000") BigDecimal ratio) {
    }

    public record PeriodResponse(Long id, String registrationNo, String unit,
                                 LocalDate startDate, LocalDate endDate, BigDecimal ratio,
                                 int versionIntroduced, Integer supersededByVersion,
                                 Long correctsPeriodId) {
        static PeriodResponse from(ServicePeriod p) {
            return new PeriodResponse(p.getId(), p.getRegistrationNo(), p.getUnit(),
                    p.getStartDate(), p.getEndDate(), p.getRatio(),
                    p.getVersionIntroduced(), p.getSupersededByVersion(), p.getCorrectsPeriodId());
        }
    }

    public record VersionResponse(int version,
                                  List<PeriodResponse> introduced,
                                  List<PeriodResponse> superseded) {
    }

    public record SegmentResponse(LocalDate start, LocalDate end, long days,
                                  BigDecimal effectiveRatio, BigDecimal years) {
        static SegmentResponse from(ServiceYearsCalculator.Segment s) {
            return new SegmentResponse(s.start(), s.end(), s.days(), s.effectiveRatio(), s.years());
        }
    }

    public record OverlapResponse(int version, BigDecimal totalYears, List<SegmentResponse> segments) {
    }

    public record DetermineRequest(@NotNull @DecimalMin("0.01") BigDecimal averageWage) {
    }

    public record DeterminationResponse(Long id, int versionUsed, BigDecimal totalYears,
                                        BigDecimal benefitBase, BigDecimal monthlyBenefit,
                                        Instant createdAt, DeterminationSnapshot snapshot) {
        static DeterminationResponse from(BenefitDetermination d, DeterminationSnapshot snapshot) {
            return new DeterminationResponse(d.getId(), d.getVersionUsed(), d.getTotalYears(),
                    d.getBenefitBase(), d.getMonthlyBenefit(), d.getCreatedAt(), snapshot);
        }
    }

    public record AdjustmentResponse(Long id, Long previousDeterminationId, Long newDeterminationId,
                                     BigDecimal difference, AdjustmentType type, Instant createdAt) {
        static AdjustmentResponse from(BenefitAdjustment a) {
            return new AdjustmentResponse(a.getId(), a.getPreviousDetermination().getId(),
                    a.getNewDetermination().getId(), a.getDifference(), a.getType(), a.getCreatedAt());
        }
    }
}
