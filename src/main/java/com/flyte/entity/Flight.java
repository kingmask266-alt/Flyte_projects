package com.flyte.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a flight in the Flyte system.
 * Only ADMINs can create or modify flights.
 */
@Entity
@Table(name = "flights")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @NotBlank(message = "Flight number is required")
    private String flightNumber;

    @Column(nullable = false)
    @NotBlank(message = "Origin is required")
    private String origin;

    @Column(nullable = false)
    @NotBlank(message = "Destination is required")
    private String destination;

    @Column(nullable = false)
    @NotNull(message = "Departure time is required")
    private LocalDateTime departureTime;

    @Column(nullable = false)
    @NotNull(message = "Arrival time is required")
    private LocalDateTime arrivalTime;

    /**
     * Base fare in the local currency (KES or USD).
     * Seat class multipliers are applied on top of this.
     */
    @Column(nullable = false)
    @Positive(message = "Base fare must be positive")
    private double baseFare;

    @Column(nullable = false)
    @Positive(message = "Available seats must be positive")
    private int availableSeats;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
