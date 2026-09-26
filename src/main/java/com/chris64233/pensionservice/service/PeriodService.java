package com.chris64233.pensionservice.service;

import com.chris64233.pensionservice.domain.MergedSegment;
import com.chris64233.pensionservice.domain.PeriodMerger;
import com.chris64233.pensionservice.domain.PersonAccount;
import com.chris64233.pensionservice.domain.ServicePeriod;
import com.chris64233.pensionservice.domain.ServiceYearVersion;
import com.chris64233.pensionservice.repo.PersonAccountRepository;
import com.chris64233.pensionservice.repo.ServicePeriodRepository;
import com.chris64233.pensionservice.repo.ServiceYearVersionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 期间登记与更正。所有变更操作先对参保人账户行加悲观写锁，
 * 保证同一参保人的登记/更正/核定串行执行：并发重叠登记不会重复累计，
 * 核定读取到的永远是一个完整的版本。
 */
@Service
public class PeriodService {

    private final EntityManager entityManager;
    private final ServicePeriodRepository periods;
    private final ServiceYearVersionRepository versions;
    private final PersonAccountRepository personAccounts;
    private final TransactionTemplate separateTx;
    /** 仅用于参保人账户首次创建的 JVM 级互斥，避免并发插入同一主键。 */
    private final Map<String, Object> creationLocks = new ConcurrentHashMap<>();

    public PeriodService(EntityManager entityManager,
                         ServicePeriodRepository periods,
                         ServiceYearVersionRepository versions,
                         PersonAccountRepository personAccounts,
                         PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.periods = periods;
        this.versions = versions;
        this.personAccounts = personAccounts;
        this.separateTx = new TransactionTemplate(transactionManager);
        this.separateTx.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    /**
     * 登记任职期间。登记号幂等：同一 (personId, registrationNo) 重复提交时，
     * 直接返回首次登记生成的版本，不产生新版本。
     */
    @Transactional
    public ServiceYearVersion registerPeriod(String personId, String registrationNo, String employer,
                                             LocalDate startDate, LocalDate endDate, BigDecimal creditRatio) {
        lockPerson(personId);
        var existing = periods.findByPersonIdAndRegistrationNo(personId, registrationNo);
        if (existing.isPresent()) {
            return versions.findByPersonIdAndTriggerRegistrationNo(personId, registrationNo)
                    .orElseThrow(() -> ApiException.conflict("登记号已存在但缺少对应版本: " + registrationNo));
        }
        validatePeriod(startDate, endDate, creditRatio);
        periods.save(new ServicePeriod(personId, registrationNo, employer, startDate, endDate,
                creditRatio, false, null));
        return createVersion(personId, registrationNo);
    }

    /**
     * 更正历史期间。原记录标记为已取代，更正内容作为新记录生效并生成新版本；
     * voided=true 表示作废该期间。登记号同样幂等。
     */
    @Transactional
    public ServiceYearVersion correctPeriod(String personId, String registrationNo, String correctsRegistrationNo,
                                            String employer, LocalDate startDate, LocalDate endDate,
                                            BigDecimal creditRatio, boolean voided) {
        lockPerson(personId);
        var existing = periods.findByPersonIdAndRegistrationNo(personId, registrationNo);
        if (existing.isPresent()) {
            return versions.findByPersonIdAndTriggerRegistrationNo(personId, registrationNo)
                    .orElseThrow(() -> ApiException.conflict("登记号已存在但缺少对应版本: " + registrationNo));
        }
        ServicePeriod target = periods.findByPersonIdAndRegistrationNo(personId, correctsRegistrationNo)
                .orElseThrow(() -> ApiException.notFound("被更正的期间不存在: " + correctsRegistrationNo));
        if (target.isSuperseded()) {
            throw ApiException.conflict("期间已被后续更正取代，请针对最新记录更正: " + correctsRegistrationNo);
        }
        ServicePeriod correction;
        if (voided) {
            correction = new ServicePeriod(personId, registrationNo, target.getEmployer(),
                    target.getStartDate(), target.getEndDate(), target.getCreditRatio(), true, correctsRegistrationNo);
        } else {
            validatePeriod(startDate, endDate, creditRatio);
            correction = new ServicePeriod(personId, registrationNo, employer, startDate, endDate,
                    creditRatio, false, correctsRegistrationNo);
        }
        target.markSuperseded();
        periods.save(correction);
        return createVersion(personId, registrationNo);
    }

    /** 基于当前生效期间集合计算重叠合并结果，生成下一个版本。调用前必须已持有参保人锁。 */
    private ServiceYearVersion createVersion(String personId, String triggerRegistrationNo) {
        List<PeriodMerger.PeriodView> effective = periods
                .findByPersonIdAndSupersededFalseAndVoidedFalse(personId).stream()
                .map(p -> new PeriodMerger.PeriodView(p.getStartDate(), p.getEndDate(), p.getCreditRatio()))
                .toList();
        List<PeriodMerger.Segment> merged = PeriodMerger.merge(effective);
        ServiceYearVersion version = new ServiceYearVersion(personId, versions.maxVersionNo(personId) + 1,
                triggerRegistrationNo, PeriodMerger.totalYears(merged));
        for (int i = 0; i < merged.size(); i++) {
            PeriodMerger.Segment s = merged.get(i);
            version.addSegment(new MergedSegment(version, i, s.startDate(), s.endDate(), s.ratio(), s.creditedYears()));
        }
        return versions.save(version);
    }

    /**
     * 对参保人账户行加悲观写锁；账户不存在时先在独立事务中创建并提交
     * （JVM 内互斥 + 唯一约束兜底），再回到当前事务加锁。
     */
    PersonAccount lockPerson(String personId) {
        PersonAccount account = entityManager.find(PersonAccount.class, personId, LockModeType.PESSIMISTIC_WRITE);
        if (account != null) {
            return account;
        }
        synchronized (creationLocks.computeIfAbsent(personId, k -> new Object())) {
            try {
                separateTx.executeWithoutResult(status -> {
                    if (!personAccounts.existsById(personId)) {
                        personAccounts.save(new PersonAccount(personId));
                    }
                });
            } catch (PersistenceException | org.springframework.dao.DataIntegrityViolationException ignored) {
                // 并发创建时唯一约束冲突，账户已存在
            }
        }
        account = entityManager.find(PersonAccount.class, personId, LockModeType.PESSIMISTIC_WRITE);
        if (account == null) {
            throw ApiException.conflict("参保人账户初始化失败: " + personId);
        }
        return account;
    }

    private static void validatePeriod(LocalDate startDate, LocalDate endDate, BigDecimal creditRatio) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw ApiException.badRequest("期间起止日期非法");
        }
        if (creditRatio == null || creditRatio.signum() <= 0 || creditRatio.compareTo(BigDecimal.ONE) > 0) {
            throw ApiException.badRequest("计入比例必须在 (0, 1] 区间");
        }
    }
}
