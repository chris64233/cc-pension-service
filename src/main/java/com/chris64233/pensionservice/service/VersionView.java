package com.chris64233.pensionservice.service;

import com.chris64233.pensionservice.domain.ServicePeriod;

import java.util.List;

/** 一个服务年限版本的变更内容：该版本引入的期间与被取代的期间。 */
public record VersionView(int version,
                          List<ServicePeriod> introduced,
                          List<ServicePeriod> superseded) {
}
