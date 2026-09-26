package com.chris64233.pensionservice.repository;

import com.chris64233.pensionservice.domain.InsuredPerson;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InsuredPersonRepository extends JpaRepository<InsuredPerson, Long> {

    Optional<InsuredPerson> findByPersonNo(String personNo);

    /**
     * 参保人行级悲观写锁：期间登记、更正与核定均先取该锁，
     * 保证同一参保人的版本递增与版本读取串行化。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InsuredPerson p where p.id = :id")
    Optional<InsuredPerson> findByIdForUpdate(@Param("id") Long id);
}
