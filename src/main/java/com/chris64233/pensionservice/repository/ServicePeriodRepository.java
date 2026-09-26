package com.chris64233.pensionservice.repository;

import com.chris64233.pensionservice.domain.ServicePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServicePeriodRepository extends JpaRepository<ServicePeriod, Long> {

    Optional<ServicePeriod> findByPersonIdAndRegistrationNo(Long personId, String registrationNo);

    List<ServicePeriod> findByPersonIdOrderById(Long personId);

    /** 查询指定版本下有效的期间：在该版本已引入且尚未被取代。 */
    @Query("select p from ServicePeriod p where p.person.id = :personId "
            + "and p.versionIntroduced <= :version "
            + "and (p.supersededByVersion is null or p.supersededByVersion > :version) "
            + "order by p.startDate, p.id")
    List<ServicePeriod> findEffectiveAt(@Param("personId") Long personId, @Param("version") int version);
}
