package com.chris64233.pensionservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 服务年限计算器：重叠处理与部分计入折算的统一规则。
 *
 * <p>规则：
 * <ul>
 *   <li>期间起止日期均为闭区间，按自然日计算；</li>
 *   <li>同一日历日被多个期间覆盖时，当日计入比例取各期间比例之和，封顶 1.0，
 *       即重叠部分不重复累计；</li>
 *   <li>年限 = 天数 × 当日有效比例 / 365，每个等比例分段保留 4 位小数（HALF_UP），
 *       累计年限为各分段之和。</li>
 * </ul>
 */
public final class ServiceYearsCalculator {

    public static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    public static final int YEAR_SCALE = 4;

    private ServiceYearsCalculator() {
    }

    /** 参与折算的期间切片。 */
    public record PeriodSlice(LocalDate start, LocalDate end, BigDecimal ratio) {
        public PeriodSlice {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(ratio, "ratio");
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("期间结束日期不能早于开始日期");
            }
        }
    }

    /** 等比例分段：[start, end] 闭区间内每日有效比例相同。 */
    public record Segment(LocalDate start, LocalDate end, long days,
                          BigDecimal effectiveRatio, BigDecimal years) {
    }

    /** 折算结果：累计年限 + 分段明细。 */
    public record Calculation(BigDecimal totalYears, List<Segment> segments) {
    }

    public static Calculation calculate(List<PeriodSlice> periods) {
        if (periods.isEmpty()) {
            return new Calculation(BigDecimal.ZERO.setScale(YEAR_SCALE), List.of());
        }
        // 扫描线：以所有期间起点和终点次日为边界，边界之间覆盖关系不变
        TreeSet<LocalDate> bounds = new TreeSet<>();
        for (PeriodSlice p : periods) {
            bounds.add(p.start());
            bounds.add(p.end().plusDays(1));
        }
        List<Segment> raw = new ArrayList<>();
        LocalDate prev = null;
        for (LocalDate bound : bounds) {
            if (prev != null && bound.isAfter(prev)) {
                BigDecimal ratioSum = BigDecimal.ZERO;
                for (PeriodSlice p : periods) {
                    if (!p.start().isAfter(prev) && !p.end().isBefore(prev)) {
                        ratioSum = ratioSum.add(p.ratio());
                    }
                }
                if (ratioSum.signum() > 0) {
                    BigDecimal effective = ratioSum.min(BigDecimal.ONE);
                    long days = ChronoUnit.DAYS.between(prev, bound);
                    BigDecimal years = effective.multiply(BigDecimal.valueOf(days))
                            .divide(DAYS_PER_YEAR, YEAR_SCALE, RoundingMode.HALF_UP);
                    raw.add(new Segment(prev, bound.minusDays(1), days, effective, years));
                }
            }
            prev = bound;
        }
        BigDecimal total = raw.stream()
                .map(Segment::years)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Calculation(total, List.copyOf(raw));
    }
}
