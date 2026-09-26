package com.chris64233.pensionservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 待遇计算规则：
 * 计发比例 = 累计年限 × 1%，上限 90%（保留 4 位小数）；
 * 待遇基数 = 参考工资 × 计发比例（保留 2 位小数，HALF_UP）；
 * 月待遇 = 待遇基数。
 */
public final class BenefitCalculator {

    public static final BigDecimal RATE_PER_YEAR = new BigDecimal("0.01");
    public static final BigDecimal MAX_RATE = new BigDecimal("0.90");
    public static final int MONEY_SCALE = 2;

    public record Benefit(BigDecimal accrualRate, BigDecimal benefitBase, BigDecimal monthlyBenefit) {
    }

    private BenefitCalculator() {
    }

    public static Benefit calculate(BigDecimal referenceWage, BigDecimal totalCreditedYears) {
        BigDecimal rate = totalCreditedYears.multiply(RATE_PER_YEAR);
        if (rate.compareTo(MAX_RATE) > 0) {
            rate = MAX_RATE;
        }
        rate = rate.setScale(PeriodMerger.YEARS_SCALE, RoundingMode.HALF_UP);
        BigDecimal base = referenceWage.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new Benefit(rate, base, base);
    }
}
