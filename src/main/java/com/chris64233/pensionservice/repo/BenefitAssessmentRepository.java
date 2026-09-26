package com.chris64233.pensionservice.repo;

import com.chris64233.pensionservice.domain.BenefitAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BenefitAssessmentRepository extends JpaRepository<BenefitAssessment, Long> {

    Optional<BenefitAssessment> findByIdAndPersonId(Long id, String personId);

    List<BenefitAssessment> findByPersonIdOrderByIdAsc(String personId);
}
