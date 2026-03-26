package com.flyte.entity.enums;

/**
 * User roles in the Flyte system.
 * ADMIN - manages flights, views all bookings.
 * AGENT - handles bookings on behalf of passengers.
 * PASSENGER - books and cancels their own flights.
 */
public enum Role {
    ADMIN,
    AGENT,
    PASSENGER
}