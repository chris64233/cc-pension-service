package com.chris64233.pensionservice.service;

import com.chris64233.pensionservice.domain.ServiceYearVersion;
import com.chris64233.pensionservice.repo.ServicePeriodRepository;
import com.chris64233.pensionservice.repo.ServiceYearVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PeriodServiceTest {

    private static final String PERSON = "p-period";
    private static final BigDecimal ONE = new BigDecimal("1");

    @Autowired
    PeriodService periodService;
    @Autowired
    ServiceYearVersionRepository versions;
    @Autowired
    ServicePeriodRepository periods;

    @Test
    void registerCreatesFirstVersion() {
        ServiceYearVersion v = periodService.registerPeriod(PERSON, "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE);
        assertEquals(1, v.getVersionNo());
        assertEquals(new BigDecimal("10.0082"), v.getTotalCreditedYears());
        assertEquals(1, v.getSegments().size());
    }

    @Test
    void duplicateRegistrationNoIsIdempotent() {
        ServiceYearVersion first = periodService.registerPeriod(PERSON, "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE);
        ServiceYearVersion second = periodService.registerPeriod(PERSON, "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE);
        assertEquals(first.getVersionNo(), second.getVersionNo());
        assertEquals(1, versions.findByPersonIdOrderByVersionNoAsc(PERSON).size());
        assertEquals(1, periods.findByPersonIdOrderByIdAsc(PERSON).size());
    }

    @Test
    void overlappingPeriodsAreNotDoubleCounted() {
        periodService.registerPeriod(PERSON, "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE);
        ServiceYearVersion v2 = periodService.registerPeriod(PERSON, "R2", "单位B",
                LocalDate.of(2005, 1, 1), LocalDate.of(2014, 12, 31), ONE);
        assertEquals(2, v2.getVersionNo());
        assertEquals(new BigDecimal("15.0110"), v2.getTotalCreditedYears());
        assertEquals(1, v2.getSegments().size());
    }

    @Test
    void correctionCreatesNewVersionAndKeepsOld() {
        periodService.registerPeriod(PERSON, "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE);
        ServiceYearVersion v2 = periodService.correctPeriod(PERSON, "C1", "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2014, 12, 31), ONE, false);
        assertEquals(2, v2.getVersionNo());
        assertEquals(new BigDecimal("15.0110"), v2.getTotalCreditedYears());
        // 旧版本保留不变
        ServiceYearVersion v1 = versions.findByPersonIdAndVersionNo(PERSON, 1).orElseThrow();
        assertEquals(new BigDecimal("10.0082"), v1.getTotalCreditedYears());
        // 原期间被标记取代，更正记录生效
        assertTrue(periods.findByPersonIdAndRegistrationNo(PERSON, "R1").orElseThrow().isSuperseded());
        assertEquals("R1", periods.findByPersonIdAndRegistrationNo(PERSON, "C1").orElseThrow()
                .getCorrectsRegistrationNo());
    }

    @Test
    void voidCorrectionRemovesPeriod() {
        periodService.registerPeriod(PERSON, "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE);
        periodService.registerPeriod(PERSON, "R2", "单位B",
                LocalDate.of(2005, 1, 1), LocalDate.of(2014, 12, 31), ONE);
        ServiceYearVersion v3 = periodService.correctPeriod(PERSON, "C1", "R1", null,
                null, null, null, true);
        assertEquals(3, v3.getVersionNo());
        // 仅剩 R2：2005-01-01..2014-12-31 共 3652 天 → 10.0055 年
        assertEquals(new BigDecimal("10.0055"), v3.getTotalCreditedYears());
        assertEquals(LocalDate.of(2005, 1, 1), v3.getSegments().get(0).getStartDate());
        assertEquals(LocalDate.of(2014, 12, 31), v3.getSegments().get(0).getEndDate());
    }

    @Test
    void correctingSupersededPeriodFails() {
        periodService.registerPeriod(PERSON, "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE);
        periodService.correctPeriod(PERSON, "C1", "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2014, 12, 31), ONE, false);
        ApiException ex = assertThrows(ApiException.class, () ->
                periodService.correctPeriod(PERSON, "C2", "R1", "单位A",
                        LocalDate.of(2000, 1, 1), LocalDate.of(2019, 12, 31), ONE, false));
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void invalidPeriodIsRejected() {
        assertThrows(ApiException.class, () -> periodService.registerPeriod(PERSON, "R1", "单位A",
                LocalDate.of(2010, 1, 1), LocalDate.of(2009, 12, 31), ONE));
        assertThrows(ApiException.class, () -> periodService.registerPeriod(PERSON, "R2", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), new BigDecimal("1.5")));
        assertThrows(ApiException.class, () -> periodService.registerPeriod(PERSON, "R3", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), BigDecimal.ZERO));
    }
}
