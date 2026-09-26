package com.chris64233.pensionservice.service;

import com.chris64233.pensionservice.domain.AdjustmentType;
import com.chris64233.pensionservice.domain.BenefitAdjustment;
import com.chris64233.pensionservice.domain.BenefitDetermination;
import com.chris64233.pensionservice.domain.InsuredPerson;
import com.chris64233.pensionservice.domain.ServicePeriod;
import com.chris64233.pensionservice.repository.BenefitAdjustmentRepository;
import com.chris64233.pensionservice.repository.BenefitDeterminationRepository;
import com.chris64233.pensionservice.repository.InsuredPersonRepository;
import com.chris64233.pensionservice.repository.ServicePeriodRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 养老待遇核心服务：期间登记、更正、核定与差额台账。
 *
 * <p>并发约定：所有变更类操作与核定均先对参保人行加悲观写锁，
 * 因此版本号单调递增、同一时刻只有一个事务能推进版本或读取版本快照，
 * 核定永远基于一个完整、一致的版本。
 */
@Service
public class PensionService {

    /** 每服务一年对应的待遇计发比例。 */
    public static final BigDecimal BENEFIT_RATE_PER_YEAR = new BigDecimal("0.01");

    private final InsuredPersonRepository personRepo;
    private final ServicePeriodRepository periodRepo;
    private final BenefitDeterminationRepository determinationRepo;
    private final BenefitAdjustmentRepository adjustmentRepo;
    private final ObjectMapper objectMapper;

    public PensionService(InsuredPersonRepository personRepo,
                          ServicePeriodRepository periodRepo,
                          BenefitDeterminationRepository determinationRepo,
                          BenefitAdjustmentRepository adjustmentRepo,
                          ObjectMapper objectMapper) {
        this.personRepo = personRepo;
        this.periodRepo = periodRepo;
        this.determinationRepo = determinationRepo;
        this.adjustmentRepo = adjustmentRepo;
        this.objectMapper = objectMapper;
    }

    // ---------- 参保人 ----------

    @Transactional
    public InsuredPerson registerPerson(String personNo, String name) {
        personRepo.findByPersonNo(personNo).ifPresent(p -> {
            throw new ConflictException("参保人编号已存在: " + personNo);
        });
        return personRepo.save(new InsuredPerson(personNo, name));
    }

    // ---------- 期间登记（幂等） ----------

    /**
     * 登记任职期间。登记号为幂等键：同一参保人下重复提交相同登记号且内容一致
     * 时返回已存在的期间，内容不一致时抛出冲突。
     */
    @Transactional
    public ServicePeriod registerPeriod(Long personId, String registrationNo, String unit,
                                        LocalDate startDate, LocalDate endDate, BigDecimal ratio) {
        InsuredPerson person = lockPerson(personId);
        Optional<ServicePeriod> existing =
                periodRepo.findByPersonIdAndRegistrationNo(personId, registrationNo);
        if (existing.isPresent()) {
            ServicePeriod p = existing.get();
            if (p.matches(unit, startDate, endDate, ratio)) {
                return p;
            }
            throw new ConflictException("登记号已存在且内容不一致: " + registrationNo);
        }
        validatePeriod(startDate, endDate, ratio);
        person.bumpVersion();
        return periodRepo.save(new ServicePeriod(person, registrationNo, unit,
                startDate, endDate, ratio, person.getCurrentVersion(), null));
    }

    // ---------- 期间更正（产生新版本） ----------

    /**
     * 更正历史期间：原期间不删除，仅标记被新版本取代；新期间使用新的登记号，
     * 版本号 +1。新登记号重复提交且内容一致时幂等返回。
     */
    @Transactional
    public ServicePeriod correctPeriod(Long personId, String originalRegistrationNo,
                                       String registrationNo, String unit,
                                       LocalDate startDate, LocalDate endDate, BigDecimal ratio) {
        InsuredPerson person = lockPerson(personId);
        if (Objects.equals(originalRegistrationNo, registrationNo)) {
            throw new BusinessException("更正须使用新的登记号，不得与原登记号相同");
        }
        Optional<ServicePeriod> duplicated =
                periodRepo.findByPersonIdAndRegistrationNo(personId, registrationNo);
        if (duplicated.isPresent()) {
            ServicePeriod p = duplicated.get();
            Long originalId = periodRepo.findByPersonIdAndRegistrationNo(personId, originalRegistrationNo)
                    .map(ServicePeriod::getId)
                    .orElse(null);
            if (p.matches(unit, startDate, endDate, ratio)
                    && Objects.equals(p.getCorrectsPeriodId(), originalId)) {
                return p;
            }
            throw new ConflictException("登记号已存在且内容不一致: " + registrationNo);
        }
        ServicePeriod original = findCurrentPeriod(personId, originalRegistrationNo);
        validatePeriod(startDate, endDate, ratio);
        person.bumpVersion();
        original.setSupersededByVersion(person.getCurrentVersion());
        return periodRepo.save(new ServicePeriod(person, registrationNo, unit,
                startDate, endDate, ratio, person.getCurrentVersion(), original.getId()));
    }

    private ServicePeriod findCurrentPeriod(Long personId, String registrationNo) {
        return periodRepo.findByPersonIdAndRegistrationNo(personId, registrationNo)
                .filter(ServicePeriod::isCurrent)
                .orElseThrow(() -> new NotFoundException(
                        "未找到可更正的当前有效期间, 登记号: " + registrationNo));
    }

    // ---------- 版本与重叠查询 ----------

    @Transactional(readOnly = true)
    public List<VersionView> listVersions(Long personId) {
        InsuredPerson person = getPerson(personId);
        List<ServicePeriod> all = periodRepo.findByPersonIdOrderById(personId);
        Map<Integer, List<ServicePeriod>> introduced = all.stream()
                .collect(Collectors.groupingBy(ServicePeriod::getVersionIntroduced));
        Map<Integer, List<ServicePeriod>> superseded = all.stream()
                .filter(p -> p.getSupersededByVersion() != null)
                .collect(Collectors.groupingBy(ServicePeriod::getSupersededByVersion));
        return IntStream.rangeClosed(1, person.getCurrentVersion())
                .mapToObj(v -> new VersionView(v,
                        introduced.getOrDefault(v, List.of()),
                        superseded.getOrDefault(v, List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServicePeriod> periodsAtVersion(Long personId, int version) {
        InsuredPerson person = getPerson(personId);
        if (version < 1 || version > person.getCurrentVersion()) {
            throw new NotFoundException("版本不存在: " + version);
        }
        return periodRepo.findEffectiveAt(personId, version);
    }

    /** 指定版本的重叠处理与折算明细。 */
    @Transactional(readOnly = true)
    public ServiceYearsCalculator.Calculation overlapAtVersion(Long personId, int version) {
        return ServiceYearsCalculator.calculate(periodsAtVersion(personId, version).stream()
                .map(ServicePeriod::toSlice)
                .toList());
    }

    // ---------- 待遇核定（锁定版本 + 不可变快照 + 差额台账） ----------

    /**
     * 生成待遇核定：在参保人行锁内读取当前版本与该版本下的期间集合，
     * 计算累计年限与待遇基数，写入不可变快照；若存在上一笔核定，
     * 同时生成差额台账记录（正为补发、负为追回），不覆盖原核定。
     */
    @Transactional
    public BenefitDetermination determine(Long personId, BigDecimal averageWage) {
        if (averageWage == null || averageWage.signum() <= 0) {
            throw new BusinessException("待遇基数（月平均工资）必须为正数");
        }
        InsuredPerson person = lockPerson(personId);
        int version = person.getCurrentVersion();
        List<ServicePeriod> periods = periodRepo.findEffectiveAt(personId, version);
        ServiceYearsCalculator.Calculation calc = ServiceYearsCalculator.calculate(
                periods.stream().map(ServicePeriod::toSlice).toList());

        BigDecimal benefitBase = averageWage.setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyBenefit = benefitBase
                .multiply(calc.totalYears())
                .multiply(BENEFIT_RATE_PER_YEAR)
                .setScale(2, RoundingMode.HALF_UP);

        DeterminationSnapshot snapshot = new DeterminationSnapshot(
                version, calc.totalYears(), benefitBase, monthlyBenefit, calc.segments());
        Optional<BenefitDetermination> previous =
                determinationRepo.findTopByPersonIdOrderByIdDesc(personId);
        BenefitDetermination determination = determinationRepo.save(new BenefitDetermination(
                person, version, calc.totalYears(), benefitBase, monthlyBenefit,
                toJson(snapshot)));

        previous.ifPresent(prev -> {
            BigDecimal difference = monthlyBenefit.subtract(prev.getMonthlyBenefit());
            AdjustmentType type = difference.signum() > 0 ? AdjustmentType.SUPPLEMENT
                    : difference.signum() < 0 ? AdjustmentType.RECOVERY
                    : AdjustmentType.NONE;
            adjustmentRepo.save(new BenefitAdjustment(person, prev, determination, difference, type));
        });
        return determination;
    }

    @Transactional(readOnly = true)
    public List<BenefitDetermination> listDeterminations(Long personId) {
        getPerson(personId);
        return determinationRepo.findByPersonIdOrderById(personId);
    }

    /** 差额链：按生成顺序排列的新旧核定差额台账。 */
    @Transactional(readOnly = true)
    public List<BenefitAdjustment> listAdjustments(Long personId) {
        getPerson(personId);
        return adjustmentRepo.findByPersonIdOrderById(personId);
    }

    public DeterminationSnapshot snapshotOf(BenefitDetermination determination) {
        try {
            return objectMapper.readValue(determination.getSnapshotJson(), DeterminationSnapshot.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("核定快照反序列化失败: " + determination.getId(), e);
        }
    }

    // ---------- 内部方法 ----------

    private InsuredPerson lockPerson(Long personId) {
        return personRepo.findByIdForUpdate(personId)
                .orElseThrow(() -> new NotFoundException("参保人不存在: " + personId));
    }

    private InsuredPerson getPerson(Long personId) {
        return personRepo.findById(personId)
                .orElseThrow(() -> new NotFoundException("参保人不存在: " + personId));
    }

    private static void validatePeriod(LocalDate startDate, LocalDate endDate, BigDecimal ratio) {
        if (startDate == null || endDate == null || ratio == null) {
            throw new BusinessException("期间起止日期与计入比例不能为空");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("期间结束日期不能早于开始日期");
        }
        if (ratio.signum() <= 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("计入比例必须在 (0, 1] 区间: " + ratio);
        }
    }

    private String toJson(DeterminationSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException e) {
            throw new IllegalStateException("核定快照序列化失败", e);
        }
    }
}
