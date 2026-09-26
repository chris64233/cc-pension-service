package com.chris64233.pensionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 参保人账户。存在即代表该参保人已被初始化；
 * 所有期间登记/更正/核定操作均先对该行加悲观写锁，保证同一参保人的变更串行化。
 */
@Entity
@Table(name = "person_account")
public class PersonAccount {

    @Id
    @Column(name = "person_id", length = 64)
    private String personId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PersonAccount() {
    }

    public PersonAccount(String personId) {
        this.personId = personId;
        this.createdAt = Instant.now();
    }

    public String getPersonId() {
        return personId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
