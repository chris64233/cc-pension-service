package com.chris64233.pensionservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 期间重叠处理与年限折算。
 *
 * 统一规则：将时间轴按所有期间的端点切分为基本区间，每个基本区间内取覆盖它的所有期间的
 * 最大计入比例（重叠时间只计一次，不重复累计）；区间折算年限 = 天数 × 比例 / 365，
 * 保留 4 位小数（HALF_UP）。相邻且比例相同的基本区间合并为一个分段。
 */
public final class PeriodMerger {

    public static final int YEARS_SCALE = 4;
    public static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");

    /** 输入的一段期间，起止日期均为闭区间。 */
    public record PeriodView(LocalDate startDate, LocalDate endDate, BigDecimal ratio) {
    }

    /** 合并后的分段，起止日期均为闭区间。 */
    public record Segment(LocalDate startDate, LocalDate endDate, BigDecimal ratio, BigDecimal creditedYears) {
    }

    /** 合并中的区间，年限在区间合并完成后统一折算，避免分段舍入累积误差。 */
    private record Interval(LocalDate startDate, LocalDate endDate, BigDecimal ratio) {
    }

    private PeriodMerger() {
    }

    public static List<Segment> merge(List<PeriodView> periods) {
        if (periods == null || periods.isEmpty()) {
            return List.of();
        }
        TreeSet<LocalDate> points = new TreeSet<>();
        for (PeriodView p : periods) {
            points.add(p.startDate());
            points.add(p.endDate().plusDays(1)); // 端点按闭区间处理，切分时转为半开区间
        }
        List<Interval> intervals = new ArrayList<>();
        LocalDate prev = null;
        for (LocalDate point : points) {
            if (prev != null && prev.isBefore(point)) {
                BigDecimal ratio = maxRatioCovering(periods, prev);
                if (ratio.signum() > 0) {
                    appendOrMerge(intervals, prev, point.minusDays(1), ratio);
                }
            }
            prev = point;
        }
        return intervals.stream()
                .map(i -> new Segment(i.startDate(), i.endDate(), i.ratio(), creditedYears(i)))
                .toList();
    }

    public static BigDecimal totalYears(List<Segment> segments) {
        return segments.stream()
                .map(Segment::creditedYears)
                .reduce(BigDecimal.ZERO.setScale(YEARS_SCALE, RoundingMode.HALF_UP), BigDecimal::add);
    }

    private static BigDecimal maxRatioCovering(List<PeriodView> periods, LocalDate day) {
        BigDecimal max = BigDecimal.ZERO;
        for (PeriodView p : periods) {
            if (!day.isBefore(p.startDate()) && !day.isAfter(p.endDate()) && p.ratio().compareTo(max) > 0) {
                max = p.ratio();
            }
        }
        return max;
    }

    private static BigDecimal creditedYears(Interval interval) {
        long days = ChronoUnit.DAYS.between(interval.startDate(), interval.endDate()) + 1;
        return BigDecimal.valueOf(days)
                .multiply(interval.ratio())
                .divide(DAYS_PER_YEAR, YEARS_SCALE, RoundingMode.HALF_UP);
    }

    private static void appendOrMerge(List<Interval> result, LocalDate start, LocalDate end, BigDecimal ratio) {
        if (!result.isEmpty()) {
            Interval last = result.get(result.size() - 1);
            if (last.ratio().compareTo(ratio) == 0 && last.endDate().plusDays(1).equals(start)) {
                result.set(result.size() - 1, new Interval(last.startDate(), end, ratio));
                return;
            }
        }
        result.add(new Interval(start, end, ratio));
    }
}
