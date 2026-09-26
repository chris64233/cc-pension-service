package com.chris64233.pensionservice.repo;

import com.chris64233.pensionservice.domain.ServiceYearVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceYearVersionRepository extends JpaRepository<ServiceYearVersion, Long> {

    Optional<ServiceYearVersion> findByPersonIdAndVersionNo(String personId, int versionNo);

    Optional<ServiceYearVersion> findByPersonIdAndTriggerRegistrationNo(String personId, String triggerRegistrationNo);

    Optional<ServiceYearVersion> findTopByPersonIdOrderByVersionNoDesc(String personId);

    List<ServiceYearVersion> findByPersonIdOrderByVersionNoAsc(String personId);

    @Query("select coalesce(max(v.versionNo), 0) from ServiceYearVersion v where v.personId = :personId")
    int maxVersionNo(@Param("personId") String personId);
}
