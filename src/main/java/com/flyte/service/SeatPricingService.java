package com.flyte.service;

import com.flyte.entity.enums.SeatClass;
import org.springframework.stereotype.Service;

/**
 * Calculates the final ticket price by applying a
 * seat class multiplier on top of the flight's base fare.
 *
 * Multipliers:
 *   ECONOMY         = 1.0x  (base price)
 *   PREMIUM_ECONOMY = 1.5x
 *   BUSINESS        = 3.0x
 *   FIRST_CLASS     = 5.0x
 */
@Service
public class SeatPricingService {

    public double calculatePrice(double baseFare, SeatClass seatClass) {
        double multiplier = switch (seatClass) {
            case ECONOMY         -> 1.0;
            case PREMIUM_ECONOMY -> 1.5;
            case BUSINESS        -> 3.0;
            case FIRST_CLASS     -> 5.0;
        };
        return baseFare * multiplier;
    }
}
