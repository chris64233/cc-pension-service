package com.chris64233.pensionservice;

import com.chris64233.pensionservice.domain.AdjustmentType;
import com.chris64233.pensionservice.domain.BenefitAdjustment;
import com.chris64233.pensionservice.domain.BenefitDetermination;
import com.chris64233.pensionservice.domain.InsuredPerson;
import com.chris64233.pensionservice.domain.ServicePeriod;
import com.chris64233.pensionservice.repository.ServicePeriodRepository;
import com.chris64233.pensionservice.service.ConflictException;
import com.chris64233.pensionservice.service.NotFoundException;
import com.chris64233.pensionservice.service.PensionService;
import com.chris64233.pensionservice.service.ServiceYearsCalculator;
import com.chris64233.pensionservice.service.VersionView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PensionServiceIntegrationTest {

    @Autowired
    private PensionService service;

    @Autowired
    private ServicePeriodRepository periodRepo;

    private static final LocalDate D2021_START = LocalDate.of(2021, 1, 1);
    private static final LocalDate D2021_END = LocalDate.of(2021, 12, 31);

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    @Test
    void duplicateRegistrationNoIsIdempotent() {
        InsuredPerson person = service.registerPerson("P-IDEM-1", "张三");
        ServicePeriod first = service.registerPeriod(person.getId(), "R1", "甲单位",
                D2021_START, D2021_END, BigDecimal.ONE);
        ServicePeriod second = service.registerPeriod(person.getId(), "R1", "甲单位",
                D2021_START, D2021_END, BigDecimal.ONE);

        assertEquals(first.getId(), second.getId());
        assertEquals(1, periodRepo.findByPersonIdOrderById(person.getId()).size());
        // 幂等命中不推进版本
        assertEquals(1, service.listVersions(person.getId()).size());
    }

    @Test
    void sameRegistrationNoWithDifferentPayloadConflicts() {
        InsuredPerson person = service.registerPerson("P-IDEM-2", "李四");
        service.registerPeriod(person.getId(), "R1", "甲单位",
                D2021_START, D2021_END, BigDecimal.ONE);
        assertThrows(ConflictException.class, () -> service.registerPeriod(person.getId(), "R1",
                "乙单位", D2021_START, D2021_END, BigDecimal.ONE));
    }

    @Test
    void invalidPeriodIsRejected() {
        InsuredPerson person = service.registerPerson("P-VAL-1", "王五");
        assertThrows(Exception.class, () -> service.registerPeriod(person.getId(), "R1", "甲单位",
                D2021_END, D2021_START, BigDecimal.ONE));
        assertThrows(Exception.class, () -> service.registerPeriod(person.getId(), "R2", "甲单位",
                D2021_START, D2021_END, new BigDecimal("1.5")));
    }

    @Test
    void correctionCreatesNewVersionAndKeepsHistory() {
        InsuredPerson person = service.registerPerson("P-VER-1", "赵六");
        service.registerPeriod(person.getId(), "R1", "甲单位",
                D2021_START, D2021_END, BigDecimal.ONE);
        service.registerPeriod(person.getId(), "R2", "乙单位",
                LocalDate.of(2021, 7, 1), D2021_END, new BigDecimal("0.5"));

        ServicePeriod correction = service.correctPeriod(person.getId(), "R2", "R2-C1", "乙单位",
                LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31), BigDecimal.ONE);

        assertEquals(3, correction.getVersionIntroduced());
        assertNotNull(correction.getCorrectsPeriodId());

        // 版本 2 下仍是原 R2 期间，版本 3 下是新期间
        List<ServicePeriod> v2 = service.periodsAtVersion(person.getId(), 2);
        assertEquals(2, v2.size());
        assertTrue(v2.stream().anyMatch(p -> p.getRegistrationNo().equals("R2")));
        List<ServicePeriod> v3 = service.periodsAtVersion(person.getId(), 3);
        assertEquals(2, v3.size());
        assertTrue(v3.stream().anyMatch(p -> p.getRegistrationNo().equals("R2-C1")));
        assertTrue(v3.stream().noneMatch(p -> p.getRegistrationNo().equals("R2")));

        // 版本列表：版本 3 引入 R2-C1、取代 R2
        List<VersionView> versions = service.listVersions(person.getId());
        assertEquals(3, versions.size());
        assertEquals("R2-C1", versions.get(2).introduced().get(0).getRegistrationNo());
        assertEquals("R2", versions.get(2).superseded().get(0).getRegistrationNo());

        // 已取代的期间不能再次更正
        assertThrows(NotFoundException.class, () -> service.correctPeriod(person.getId(),
                "R2", "R2-C2", "乙单位", D2021_START, D2021_END, BigDecimal.ONE));
    }

    @Test
    void correctionIsIdempotentOnRetry() {
        InsuredPerson person = service.registerPerson("P-VER-2", "孙七");
        service.registerPeriod(person.getId(), "R1", "甲单位",
                D2021_START, D2021_END, BigDecimal.ONE);
        ServicePeriod first = service.correctPeriod(person.getId(), "R1", "R1-C1", "甲单位",
                D2021_START, LocalDate.of(2021, 6, 30), BigDecimal.ONE);
        ServicePeriod retry = service.correctPeriod(person.getId(), "R1", "R1-C1", "甲单位",
                D2021_START, LocalDate.of(2021, 6, 30), BigDecimal.ONE);
        assertEquals(first.getId(), retry.getId());
        assertEquals(2, periodRepo.findByPersonIdOrderById(person.getId()).size());
    }

    @Test
    void determinationLocksVersionAndOverlapsAreNotDoubleCounted() {
        InsuredPerson person = service.registerPerson("P-DET-1", "周八");
        service.registerPeriod(person.getId(), "R1", "甲单位",
                D2021_START, D2021_END, BigDecimal.ONE);
        service.registerPeriod(person.getId(), "R2", "乙单位",
                LocalDate.of(2021, 7, 1), D2021_END, new BigDecimal("0.5"));

        BenefitDetermination det = service.determine(person.getId(), new BigDecimal("8000"));

        assertEquals(2, det.getVersionUsed());
        // 重叠的下半年比例 1+0.5 封顶 1.0，全年按 1.0 计
        assertAmount("1.0000", det.getTotalYears());
        assertAmount("8000.00", det.getBenefitBase());
        // 8000 × 1.0000 × 1% = 80.00
        assertAmount("80.00", det.getMonthlyBenefit());
        // 快照包含分段明细
        assertEquals(2, service.snapshotOf(det).segments().size());
    }

    @Test
    void redeterminationAfterCorrectionKeepsOriginalAndRecordsAdjustmentChain() {
        InsuredPerson person = service.registerPerson("P-ADJ-1", "吴九");
        service.registerPeriod(person.getId(), "R1", "甲单位",
                D2021_START, D2021_END, BigDecimal.ONE);
        service.registerPeriod(person.getId(), "R2", "乙单位",
                LocalDate.of(2021, 7, 1), D2021_END, new BigDecimal("0.5"));
        BenefitDetermination first = service.determine(person.getId(), new BigDecimal("8000"));

        // 更正一：R2 实际为 2022 年全职 → 累计 2 年，待遇提高（补发）
        service.correctPeriod(person.getId(), "R2", "R2-C1", "乙单位",
                LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31), BigDecimal.ONE);
        BenefitDetermination second = service.determine(person.getId(), new BigDecimal("8000"));
        assertEquals(3, second.getVersionUsed());
        assertAmount("2.0000", second.getTotalYears());
        assertAmount("160.00", second.getMonthlyBenefit());

        // 更正二：R1 实际只到 2021-06-30 → 累计 1.4959 年，待遇降低（追回）
        service.correctPeriod(person.getId(), "R1", "R1-C1", "甲单位",
                D2021_START, LocalDate.of(2021, 6, 30), BigDecimal.ONE);
        BenefitDetermination third = service.determine(person.getId(), new BigDecimal("8000"));
        assertEquals(4, third.getVersionUsed());
        assertAmount("1.4959", third.getTotalYears());
        assertAmount("119.67", third.getMonthlyBenefit());

        // 原核定不被覆盖
        List<BenefitDetermination> dets = service.listDeterminations(person.getId());
        assertEquals(3, dets.size());
        BenefitDetermination original = dets.get(0);
        assertEquals(first.getId(), original.getId());
        assertEquals(2, original.getVersionUsed());
        assertAmount("1.0000", original.getTotalYears());
        assertAmount("80.00", original.getMonthlyBenefit());

        // 差额链：第一笔补发 80.00，第二笔追回 -40.33，均挂接前后核定
        List<BenefitAdjustment> chain = service.listAdjustments(person.getId());
        assertEquals(2, chain.size());
        BenefitAdjustment supplement = chain.get(0);
        assertEquals(AdjustmentType.SUPPLEMENT, supplement.getType());
        assertAmount("80.00", supplement.getDifference());
        assertEquals(first.getId(), supplement.getPreviousDetermination().getId());
        assertEquals(second.getId(), supplement.getNewDetermination().getId());
        BenefitAdjustment recovery = chain.get(1);
        assertEquals(AdjustmentType.RECOVERY, recovery.getType());
        assertAmount("-40.33", recovery.getDifference());
        assertEquals(second.getId(), recovery.getPreviousDetermination().getId());
        assertEquals(third.getId(), recovery.getNewDetermination().getId());
    }

    @Test
    void overlapQueryMatchesDeterminationSnapshot() {
        InsuredPerson person = service.registerPerson("P-QRY-1", "郑十");
        service.registerPeriod(person.getId(), "R1", "甲单位",
                D2021_START, D2021_END, BigDecimal.ONE);
        service.registerPeriod(person.getId(), "R2", "乙单位",
                LocalDate.of(2021, 7, 1), D2021_END, new BigDecimal("0.5"));
        BenefitDetermination det = service.determine(person.getId(), new BigDecimal("8000"));

        ServiceYearsCalculator.Calculation calc =
                service.overlapAtVersion(person.getId(), det.getVersionUsed());
        assertAmount(det.getTotalYears().toPlainString(), calc.totalYears());
        assertEquals(calc.segments(), service.snapshotOf(det).segments());
        assertTrue(det.getSnapshotJson() != null && !det.getSnapshotJson().isBlank());
    }
}
