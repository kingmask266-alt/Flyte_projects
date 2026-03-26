package com.flyte.entity.enums;

/**
 * Tracks the current state of a payment transaction.
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}
