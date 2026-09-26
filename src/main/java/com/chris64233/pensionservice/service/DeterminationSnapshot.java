package com.chris64233.pensionservice.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 核定快照内容：锁定的版本、累计年限、待遇基数、月待遇额与分段折算明细。
 * 以 JSON 形式随核定记录持久化，生成后不可修改。
 */
public record DeterminationSnapshot(
        int version,
        BigDecimal totalYears,
        BigDecimal benefitBase,
        BigDecimal monthlyBenefit,
        List<ServiceYearsCalculator.Segment> segments) {
}
