package com.chris64233.pensionservice.repo;

import com.chris64233.pensionservice.domain.PersonAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonAccountRepository extends JpaRepository<PersonAccount, String> {
}
