package com.chris64233.pensionservice.service;

import com.chris64233.pensionservice.domain.BenefitAssessment;
import com.chris64233.pensionservice.domain.ServiceYearVersion;
import com.chris64233.pensionservice.web.Views;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发场景测试。注意本类不能加 @Transactional：并发线程需要看到彼此已提交的数据。
 */
@SpringBootTest
class ConcurrencyTest {

    private static final BigDecimal ONE = new BigDecimal("1");
    private static final BigDecimal WAGE = new BigDecimal("10000");

    @Autowired
    PeriodService periodService;
    @Autowired
    AssessmentService assessmentService;
    @Autowired
    PensionQueryService queryService;

    private static String person() {
        return "p-" + UUID.randomUUID();
    }

    @Test
    void concurrentOverlappingRegistrationsNeverDoubleCount() throws Exception {
        String personId = person();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<ServiceYearVersion>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String regNo = "R" + i;
            futures.add(pool.submit(() -> {
                gate.await();
                // 完全重叠的期间：任何时刻累计年限都只能是 10.0082
                return periodService.registerPeriod(personId, regNo, "单位" + regNo,
                        LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE);
            }));
        }
        gate.countDown();
        for (Future<ServiceYearVersion> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        List<Views.VersionView> versions = queryService.listVersions(personId);
        assertEquals(threads, versions.size());
        for (Views.VersionView v : versions) {
            assertEquals(new BigDecimal("10.0082"), v.totalCreditedYears());
        }
    }

    @Test
    void concurrentDuplicateRegistrationIsIdempotent() throws Exception {
        String personId = person();
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<ServiceYearVersion>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                gate.await();
                return periodService.registerPeriod(personId, "R-SAME", "单位A",
                        LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE);
            }));
        }
        gate.countDown();
        for (Future<ServiceYearVersion> f : futures) {
            assertEquals(1, f.get(30, TimeUnit.SECONDS).getVersionNo());
        }
        pool.shutdown();

        assertEquals(1, queryService.listVersions(personId).size());
        assertEquals(1, queryService.listPeriods(personId).size());
    }

    @Test
    void assessmentConcurrentWithCorrectionUsesOneCompleteVersion() throws Exception {
        String personId = person();
        periodService.registerPeriod(personId, "R1", "单位A",
                LocalDate.of(2000, 1, 1), LocalDate.of(2009, 12, 31), ONE);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        Future<ServiceYearVersion> correction = pool.submit(() -> {
            gate.await();
            return periodService.correctPeriod(personId, "C1", "R1", "单位A",
                    LocalDate.of(2000, 1, 1), LocalDate.of(2014, 12, 31), ONE, false);
        });
        Future<BenefitAssessment> assessment = pool.submit(() -> {
            gate.await();
            return assessmentService.assess(personId, WAGE);
        });
        gate.countDown();
        correction.get(30, TimeUnit.SECONDS);
        BenefitAssessment a = assessment.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // 更正与核定串行化：核定要么基于旧版本 1，要么基于新版本 2，且快照与该版本完全一致
        List<Views.VersionView> versions = queryService.listVersions(personId);
        assertEquals(2, versions.size());
        Views.VersionView locked = a.getVersionNo() == 1 ? versions.get(0) : versions.get(1);
        assertEquals(locked.totalCreditedYears(), a.getTotalCreditedYears());
        assertEquals(locked.segments().size(), a.getSegments().size());
        assertTrue(a.getVersionNo() == 1 || a.getVersionNo() == 2);
    }
}
