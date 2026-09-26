package com.chris64233.pensionservice.service;

import com.chris64233.pensionservice.service.ServiceYearsCalculator.Calculation;
import com.chris64233.pensionservice.service.ServiceYearsCalculator.PeriodSlice;
import com.chris64233.pensionservice.service.ServiceYearsCalculator.Segment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceYearsCalculatorTest {

    private static PeriodSlice slice(String start, String end, String ratio) {
        return new PeriodSlice(LocalDate.parse(start), LocalDate.parse(end), new BigDecimal(ratio));
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    @Test
    void emptyPeriodsYieldZeroYears() {
        Calculation calc = ServiceYearsCalculator.calculate(List.of());
        assertAmount("0.0000", calc.totalYears());
        assertTrue(calc.segments().isEmpty());
    }

    @Test
    void singleFullYearPeriod() {
        Calculation calc = ServiceYearsCalculator.calculate(
                List.of(slice("2021-01-01", "2021-12-31", "1")));
        assertAmount("1.0000", calc.totalYears());
        assertEquals(1, calc.segments().size());
        assertEquals(365, calc.segments().get(0).days());
    }

    @Test
    void partialRatioIsProrated() {
        Calculation calc = ServiceYearsCalculator.calculate(
                List.of(slice("2021-01-01", "2021-12-31", "0.5")));
        assertAmount("0.5000", calc.totalYears());
    }

    @Test
    void fullyOverlappingPeriodsAreNotDoubleCounted() {
        Calculation calc = ServiceYearsCalculator.calculate(List.of(
                slice("2021-01-01", "2021-12-31", "1"),
                slice("2021-01-01", "2021-12-31", "1")));
        assertAmount("1.0000", calc.totalYears());
    }

    @Test
    void overlappingRatioSumIsCappedAtOne() {
        // 两个 0.6 比例的期间完全重叠，重叠日比例按 1.0 计
        Calculation calc = ServiceYearsCalculator.calculate(List.of(
                slice("2021-01-01", "2021-12-31", "0.6"),
                slice("2021-01-01", "2021-12-31", "0.6")));
        assertAmount("1.0000", calc.totalYears());
    }

    @Test
    void partialOverlapSplitsIntoSegments() {
        // A: 全年 1.0；B: 下半年 0.5 → 上半年 1.0，下半年封顶 1.0
        Calculation calc = ServiceYearsCalculator.calculate(List.of(
                slice("2021-01-01", "2021-12-31", "1"),
                slice("2021-07-01", "2021-12-31", "0.5")));
        assertAmount("1.0000", calc.totalYears());
        assertEquals(2, calc.segments().size());
        Segment first = calc.segments().get(0);
        Segment second = calc.segments().get(1);
        assertEquals(LocalDate.parse("2021-01-01"), first.start());
        assertEquals(LocalDate.parse("2021-06-30"), first.end());
        assertEquals(181, first.days());
        assertAmount("1", first.effectiveRatio());
        assertAmount("0.4959", first.years());
        assertEquals(LocalDate.parse("2021-07-01"), second.start());
        assertEquals(LocalDate.parse("2021-12-31"), second.end());
        assertEquals(184, second.days());
        assertAmount("0.5041", second.years());
    }

    @Test
    void nonOverlappingPartialRatiosAreNotCapped() {
        // 两个不重叠的 0.5 期间各自折算
        Calculation calc = ServiceYearsCalculator.calculate(List.of(
                slice("2021-01-01", "2021-06-30", "0.5"),
                slice("2021-07-01", "2021-12-31", "0.5")));
        // 181*0.5/365 = 0.2479(45...)→0.2479；184*0.5/365 = 0.2520(54...)→0.2521
        assertAmount("0.5000", calc.totalYears());
        assertEquals(2, calc.segments().size());
        assertAmount("0.5", calc.segments().get(0).effectiveRatio());
        assertAmount("0.5", calc.segments().get(1).effectiveRatio());
    }

    @Test
    void gapBetweenPeriodsIsExcluded() {
        Calculation calc = ServiceYearsCalculator.calculate(List.of(
                slice("2021-01-01", "2021-01-31", "1"),
                slice("2021-03-01", "2021-03-31", "1")));
        assertEquals(2, calc.segments().size());
        // 31/365 = 0.0849(31...)→0.0849；两段共 0.1698
        assertAmount("0.1698", calc.totalYears());
    }

    @Test
    void leapDayIsCounted() {
        Calculation calc = ServiceYearsCalculator.calculate(
                List.of(slice("2020-01-01", "2020-12-31", "1")));
        assertEquals(366, calc.segments().get(0).days());
        assertAmount("1.0027", calc.totalYears());
    }

    @Test
    void endBeforeStartIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> slice("2021-06-01", "2021-01-01", "1"));
    }
}
