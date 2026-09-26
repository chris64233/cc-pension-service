package com.chris64233.pensionservice.service;

/** 幂等键冲突等业务冲突（409）。 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
