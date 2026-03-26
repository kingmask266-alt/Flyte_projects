package com.flyte;

import com.flyte.entity.enums.SeatClass;
import com.flyte.service.SeatPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for SeatPricingService.
 * Verifies that seat class multipliers are applied correctly.
 *
 * These are PURE unit tests — no Spring context, no database, no network.
 * They run instantly and test logic only.
 */
class SeatPricingServiceTest {

    private SeatPricingService seatPricingService;

    private static final double BASE_FARE = 100.0;
    private static final double DELTA = 0.001; // Allowed floating point margin

    @BeforeEach
    void setUp() {
        seatPricingService = new SeatPricingService();
    }

    @Test
    @DisplayName("Economy class: 1.0x multiplier → same as base fare")
    void testEconomyPrice() {
        double price = seatPricingService.calculatePrice(BASE_FARE, SeatClass.ECONOMY);
        assertEquals(100.0, price, DELTA, "Economy should cost 1x base fare");
    }

    @Test
    @DisplayName("Premium Economy: 1.5x multiplier")
    void testPremiumEconomyPrice() {
        double price = seatPricingService.calculatePrice(BASE_FARE, SeatClass.PREMIUM_ECONOMY);
        assertEquals(150.0, price, DELTA, "Premium Economy should cost 1.5x base fare");
    }

    @Test
    @DisplayName("Business class: 3.0x multiplier")
    void testBusinessPrice() {
        double price = seatPricingService.calculatePrice(BASE_FARE, SeatClass.BUSINESS);
        assertEquals(300.0, price, DELTA, "Business should cost 3x base fare");
    }

    @Test
    @DisplayName("First Class: 5.0x multiplier")
    void testFirstClassPrice() {
        double price = seatPricingService.calculatePrice(BASE_FARE, SeatClass.FIRST_CLASS);
        assertEquals(500.0, price, DELTA, "First Class should cost 5x base fare");
    }

    @Test
    @DisplayName("Price scales correctly with different base fares")
    void testPriceWithDifferentBaseFare() {
        double price = seatPricingService.calculatePrice(200.0, SeatClass.BUSINESS);
        assertEquals(600.0, price, DELTA, "200 base * 3x = 600 for Business");
    }
}
