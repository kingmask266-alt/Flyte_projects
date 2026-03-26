package com.flyte.service;

import com.flyte.dto.BookingRequest;
import com.flyte.entity.Booking;
import com.flyte.entity.Flight;
import com.flyte.entity.User;
import com.flyte.exception.ResourceNotFoundException;
import com.flyte.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles all booking operations for CUSTOMER users.
 * Manages seat availability and price calculation.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final FlightService flightService;
    private final UserService userService;
    private final SeatPricingService seatPricingService;

    /**
     * Book a flight for an authenticated CUSTOMER.
     * Validates seat availability, calculates price, and saves the booking.
     */
    @Transactional
    public Booking bookFlight(BookingRequest request, String username) {
        Flight flight = flightService.getFlightByNumber(request.getFlightNumber());
        User user = userService.findByUsername(username);

        if (flight.getAvailableSeats() <= 0) {
            throw new IllegalStateException("No available seats on flight: " + request.getFlightNumber());
        }

        if (bookingRepository.existsByFlightIdAndSeatNumber(flight.getId(), request.getSeatNumber())) {
            throw new IllegalStateException("Seat " + request.getSeatNumber() + " is already booked.");
        }

        double finalPrice = seatPricingService.calculatePrice(flight.getBaseFare(), request.getSeatClass());

        Booking booking = Booking.builder()
                .passengerName(request.getPassengerName())
                .seatClass(request.getSeatClass())
                .seatNumber(request.getSeatNumber())
                .price(finalPrice)
                .flight(flight)
                .user(user)
                .cancelled(false)
                .build();

        // Reduce available seats
        flight.setAvailableSeats(flight.getAvailableSeats() - 1);

        return bookingRepository.save(booking);
    }

    /**
     * Cancel a booking by ID.
     * Only the booking owner can cancel their booking.
     */
    @Transactional
    public Booking cancelBooking(Long bookingId, String username) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (!booking.getUser().getUsername().equals(username)) {
            throw new SecurityException("You are not authorized to cancel this booking.");
        }

        if (booking.isCancelled()) {
            throw new IllegalStateException("Booking is already cancelled.");
        }

        booking.setCancelled(true);

        // Restore the seat
        booking.getFlight().setAvailableSeats(booking.getFlight().getAvailableSeats() + 1);

        return bookingRepository.save(booking);
    }

    /**
     * Get all bookings for the currently logged-in user.
     */
    public List<Booking> getMyBookings(String username) {
        User user = userService.findByUsername(username);
        return bookingRepository.findByUserId(user.getId());
    }

    /**
     * Get all bookings across all flights (ADMIN only).
     */
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }
}
