package com.chris64233.pensionservice.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodMergerTest {

    private static final BigDecimal ONE = new BigDecimal("1");

    @Test
    void nonOverlappingPeriodsAreSummed() {
        List<PeriodMerger.Segment> segments = PeriodMerger.merge(List.of(
                new PeriodMerger.PeriodView(LocalDate.of(2000, 1, 1), LocalDate.of(2000, 12, 31), ONE),
                new PeriodMerger.PeriodView(LocalDate.of(2002, 1, 1), LocalDate.of(2002, 12, 31), ONE)));
        // 2000 为闰年 366 天 → 1.0027 年，2002 年 365 天 → 1.0000 年
        assertEquals(2, segments.size());
        assertEquals(new BigDecimal("2.0027"), PeriodMerger.totalYears(segments));
    }

    @Test
    void overlappingPeriodsAreNotDoubleCounted() {
        List<PeriodMerger.Segment> segments = PeriodMerger.merge(List.of(
                new PeriodMerger.PeriodView(LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE),
                new PeriodMerger.PeriodView(LocalDate.of(2005, 1, 1), LocalDate.of(2014, 12, 31), ONE)));
        // 并集 2000-01-01..2014-12-31 共 5479 天，重叠的 5 年只计一次
        assertEquals(1, segments.size());
        assertEquals(LocalDate.of(2000, 1, 1), segments.get(0).startDate());
        assertEquals(LocalDate.of(2014, 12, 31), segments.get(0).endDate());
        assertEquals(new BigDecimal("15.0110"), PeriodMerger.totalYears(segments));
    }

    @Test
    void partialRatioIsProrated() {
        List<PeriodMerger.Segment> segments = PeriodMerger.merge(List.of(
                new PeriodMerger.PeriodView(LocalDate.of(2000, 1, 1), LocalDate.of(2000, 12, 31),
                        new BigDecimal("0.5"))));
        // 366 天 × 0.5 / 365 = 0.5014 年
        assertEquals(new BigDecimal("0.5014"), PeriodMerger.totalYears(segments));
    }

    @Test
    void overlapUsesMaxRatio() {
        List<PeriodMerger.Segment> segments = PeriodMerger.merge(List.of(
                new PeriodMerger.PeriodView(LocalDate.of(2000, 1, 1), LocalDate.of(2000, 12, 31), ONE),
                new PeriodMerger.PeriodView(LocalDate.of(2000, 7, 1), LocalDate.of(2001, 6, 30),
                        new BigDecimal("0.5"))));
        // 重叠段取较大比例 1.0，只有后半段按 0.5 单独折算
        assertEquals(2, segments.size());
        assertEquals(new PeriodMerger.Segment(LocalDate.of(2000, 1, 1), LocalDate.of(2000, 12, 31),
                ONE, new BigDecimal("1.0027")), segments.get(0));
        assertEquals(new PeriodMerger.Segment(LocalDate.of(2001, 1, 1), LocalDate.of(2001, 6, 30),
                new BigDecimal("0.5"), new BigDecimal("0.2479")), segments.get(1));
        assertEquals(new BigDecimal("1.2506"), PeriodMerger.totalYears(segments));
    }

    @Test
    void emptyPeriodsYieldNoSegments() {
        assertEquals(List.of(), PeriodMerger.merge(List.of()));
        assertEquals(new BigDecimal("0.0000"), PeriodMerger.totalYears(List.of()));
    }
}
