package com.chris64233.pensionservice.service;

/** 业务参数校验失败（400）。 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
