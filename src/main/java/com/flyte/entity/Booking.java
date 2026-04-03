package com.flyte.entity;

import com.flyte.entity.enums.SeatClass;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Represents a flight booking made by a PASSENGER.
 * Each booking is linked to one Flight and one User.
 */
@Entity
@Table(name = "bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotBlank(message = "Passenger name is required")
    private String passengerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Seat class is required")
    private SeatClass seatClass;

    /**
     * Final price after applying seat class multiplier to base fare.
     */
    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private String seatNumber;

    @Column(nullable = false)
    private boolean cancelled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Flight flight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User user;

    @Column(nullable = false, updatable = false)
    private LocalDateTime bookedAt;
    private String ticketNumber;

    @PrePersist
    protected void onCreate() {
        this.bookedAt = LocalDateTime.now();
        this.ticketNumber = "GENERATING"; // Temporary, will update after ID assignment
    }

    public static String generateTicketNumber(Long bookingId, LocalDateTime bookedAt) {
        String date = bookedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("FLY-%s-%06d", date, bookingId);
    }




}
