package com.flyte.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String passengerName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    // Status: Confirmed, Pending, Cancelled
    private String status;

    // ─── Constructors ───────────────────────────────────────────────
    public Booking() {}

    public Booking(String passengerName, Flight flight, String status) {
        this.passengerName = passengerName;
        this.flight = flight;
        this.status = status;
    }

    // ─── Getters & Setters ──────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public Flight getFlight() { return flight; }
    public void setFlight(Flight flight) { this.flight = flight; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
