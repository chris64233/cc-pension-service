package com.chris64233.pensionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 参保人。currentVersion 是服务年限期间集合的版本号，
 * 每次期间登记或更正时单调递增，核定时锁定该版本。
 */
@Entity
@Table(name = "insured_person")
public class InsuredPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_no", nullable = false, unique = true, length = 64)
    private String personNo;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    protected InsuredPerson() {
    }

    public InsuredPerson(String personNo, String name) {
        this.personNo = personNo;
        this.name = name;
        this.currentVersion = 0;
    }

    public void bumpVersion() {
        this.currentVersion++;
    }

    public Long getId() {
        return id;
    }

    public String getPersonNo() {
        return personNo;
    }

    public String getName() {
        return name;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }
}
