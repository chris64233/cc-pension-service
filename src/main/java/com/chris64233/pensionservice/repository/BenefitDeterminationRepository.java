package com.chris64233.pensionservice.repository;

import com.chris64233.pensionservice.domain.BenefitDetermination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BenefitDeterminationRepository extends JpaRepository<BenefitDetermination, Long> {

    List<BenefitDetermination> findByPersonIdOrderById(Long personId);

    Optional<BenefitDetermination> findTopByPersonIdOrderByIdDesc(Long personId);
}
