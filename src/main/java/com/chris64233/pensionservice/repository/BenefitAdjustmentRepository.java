package com.chris64233.pensionservice.repository;

import com.chris64233.pensionservice.domain.BenefitAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BenefitAdjustmentRepository extends JpaRepository<BenefitAdjustment, Long> {

    List<BenefitAdjustment> findByPersonIdOrderById(Long personId);
}
