package com.flyte;

import com.flyte.entity.Booking;
import com.flyte.entity.Flight;
import com.flyte.repository.BookingRepository;
import com.flyte.repository.FlightRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Override
    public void run(String... args) {
        // Only seed if DB is empty
        if (flightRepository.count() > 0) return;

        // ─── Seed Flights ───────────────────────────────────────────
        Flight f1 = flightRepository.save(new Flight(
                "FLY-001", "Nairobi", "London",
                LocalDateTime.of(2025, 6, 15, 8, 0),
                LocalDateTime.of(2025, 6, 15, 20, 0)));

        Flight f2 = flightRepository.save(new Flight(
                "FLY-002", "Lagos", "Dubai",
                LocalDateTime.of(2025, 6, 16, 10, 30),
                LocalDateTime.of(2025, 6, 16, 22, 0)));

        Flight f3 = flightRepository.save(new Flight(
                "FLY-003", "Johannesburg", "New York",
                LocalDateTime.of(2025, 6, 17, 6, 0),
                LocalDateTime.of(2025, 6, 17, 23, 45)));

        Flight f4 = flightRepository.save(new Flight(
                "FLY-004", "Cairo", "Paris",
                LocalDateTime.of(2025, 6, 18, 14, 0),
                LocalDateTime.of(2025, 6, 18, 19, 30)));

        // ─── Seed Bookings ──────────────────────────────────────────
        bookingRepository.save(new Booking("Alice Kamau", f1, "Confirmed"));
        bookingRepository.save(new Booking("Brian Osei", f1, "Pending"));
        bookingRepository.save(new Booking("Chidi Nwosu", f2, "Confirmed"));
        bookingRepository.save(new Booking("Diana Mensah", f3, "Cancelled"));
        bookingRepository.save(new Booking("Emmanuel Tadesse", f4, "Confirmed"));
        bookingRepository.save(new Booking("Fatima Al-Hassan", f2, "Pending"));

        System.out.println("✅ Flyte seed data loaded successfully!");
    }
}
