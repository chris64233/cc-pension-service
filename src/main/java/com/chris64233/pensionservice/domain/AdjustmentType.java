package com.chris64233.pensionservice.domain;

/**
 * 差额类型：补发（新核定高于原核定）、追回（新核定低于原核定）、无差额。
 */
public enum AdjustmentType {
    SUPPLEMENT,
    RECOVERY,
    NONE
}
