package com.chris64233.pensionservice.repo;

import com.chris64233.pensionservice.domain.ServicePeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServicePeriodRepository extends JpaRepository<ServicePeriod, Long> {

    Optional<ServicePeriod> findByPersonIdAndRegistrationNo(String personId, String registrationNo);

    List<ServicePeriod> findByPersonIdAndSupersededFalseAndVoidedFalse(String personId);

    List<ServicePeriod> findByPersonIdOrderByIdAsc(String personId);
}
