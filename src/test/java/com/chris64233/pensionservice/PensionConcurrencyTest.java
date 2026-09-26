package com.chris64233.pensionservice;

import com.chris64233.pensionservice.domain.BenefitAdjustment;
import com.chris64233.pensionservice.domain.BenefitDetermination;
import com.chris64233.pensionservice.domain.InsuredPerson;
import com.chris64233.pensionservice.repository.ServicePeriodRepository;
import com.chris64233.pensionservice.service.PensionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 并发正确性：同一参保人的登记、更正与核定通过行级悲观锁串行化。
 */
@SpringBootTest
class PensionConcurrencyTest {

    @Autowired
    private PensionService service;

    @Autowired
    private ServicePeriodRepository periodRepo;

    private static final LocalDate START = LocalDate.of(2021, 1, 1);
    private static final LocalDate END = LocalDate.of(2021, 12, 31);

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    private static List<Future<?>> runConcurrently(int threads, Callable<?> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发任务未在 60 秒内完成");
        }
        return futures;
    }

    @Test
    void concurrentOverlappingRegistrationsNeverDoubleCount() throws Exception {
        InsuredPerson person = service.registerPerson("P-CON-1", "并发甲");
        Long personId = person.getId();
        int threads = 8;

        List<Future<?>> futures = runConcurrently(threads, () -> {
            // 每个线程登记一个完全重叠、比例 1.0 的期间
            service.registerPeriod(personId, "R-" + Thread.currentThread().getId(), "单位",
                    START, END, BigDecimal.ONE);
            return null;
        });
        for (Future<?> f : futures) {
            f.get();
        }

        // 8 个期间全部落库，版本恰好推进 8 次
        assertEquals(8, periodRepo.findByPersonIdOrderById(personId).size());
        assertEquals(8, service.listVersions(personId).size());
        // 重叠部分不重复累计：8 个全额期间覆盖同一年，累计仍为 1 年
        assertAmount("1.0000", service.overlapAtVersion(personId, 8).totalYears());
    }

    @Test
    void concurrentDuplicateRegistrationIsIdempotent() throws Exception {
        InsuredPerson person = service.registerPerson("P-CON-2", "并发乙");
        Long personId = person.getId();
        int threads = 10;

        List<Future<?>> futures = runConcurrently(threads, () -> {
            service.registerPeriod(personId, "R-DUP", "单位", START, END, BigDecimal.ONE);
            return null;
        });
        for (Future<?> f : futures) {
            f.get();
        }

        // 同一登记号并发提交：只落库一条，版本只推进一次
        assertEquals(1, periodRepo.findByPersonIdOrderById(personId).size());
        assertEquals(1, service.listVersions(personId).size());
    }

    @Test
    void concurrentCorrectionsAndDeterminationsStayVersionConsistent() throws Exception {
        InsuredPerson person = service.registerPerson("P-CON-3", "并发丙");
        Long personId = person.getId();
        // 预备 4 个期间（版本 1..4）
        for (int i = 1; i <= 4; i++) {
            service.registerPeriod(personId, "R" + i, "单位" + i,
                    LocalDate.of(2000 + i, 1, 1), LocalDate.of(2000 + i, 12, 31), BigDecimal.ONE);
        }

        int corrections = 4;
        int determinations = 3;
        ExecutorService pool = Executors.newFixedThreadPool(corrections + determinations);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> all = new ArrayList<>();
        for (int i = 1; i <= corrections; i++) {
            int n = i;
            all.add(pool.submit(() -> {
                go.await();
                service.correctPeriod(personId, "R" + n, "R" + n + "-C1", "单位" + n,
                        LocalDate.of(2000 + n, 1, 1), LocalDate.of(2000 + n, 6, 30), BigDecimal.ONE);
                return null;
            }));
        }
        for (int i = 0; i < determinations; i++) {
            all.add(pool.submit(() -> {
                go.await();
                service.determine(personId, new BigDecimal("6000"));
                return null;
            }));
        }
        go.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发任务未在 60 秒内完成");
        }
        for (Future<?> f : all) {
            f.get();
        }

        // 4 次更正各推进一个版本
        assertEquals(8, service.listVersions(personId).size());

        // 每笔核定都必须与其锁定版本的重算结果完全一致（核定只使用一个完整版本）
        List<BenefitDetermination> dets = service.listDeterminations(personId);
        assertEquals(determinations, dets.size());
        for (BenefitDetermination det : dets) {
            BigDecimal recomputed =
                    service.overlapAtVersion(personId, det.getVersionUsed()).totalYears();
            assertAmount(det.getTotalYears().toPlainString(), recomputed);
        }

        // 差额链完整：n 笔核定产生 n-1 条台账，且链式挂接
        List<BenefitAdjustment> chain = service.listAdjustments(personId);
        assertEquals(determinations - 1, chain.size());
        for (int i = 0; i < chain.size(); i++) {
            assertEquals(dets.get(i).getId(), chain.get(i).getPreviousDetermination().getId());
            assertEquals(dets.get(i + 1).getId(), chain.get(i).getNewDetermination().getId());
        }
    }
}
