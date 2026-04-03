package com.flyte.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flyte.dto.BookingRequest;
import com.flyte.entity.Booking;
import com.flyte.entity.Flight;
import com.flyte.entity.User;
import com.flyte.exception.ResourceNotFoundException;
import com.flyte.repository.BookingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final FlightService flightService;
    private final UserService userService;
    private final SeatPricingService seatPricingService;

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

        flight.setAvailableSeats(flight.getAvailableSeats() - 1);
        booking = bookingRepository.save(booking); // Save to get ID
        booking.setTicketNumber(Booking.generateTicketNumber(booking.getId(), booking.getBookedAt()));
        return bookingRepository.save(booking); // Update with ticket
    }


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

        if (booking.getFlight().getDepartureTime().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new IllegalStateException("Cannot cancel a flight departing in less than 2 hours.");
        }

        booking.setCancelled(true);
        booking.getFlight().setAvailableSeats(booking.getFlight().getAvailableSeats() + 1);
        return bookingRepository.save(booking);
    }

    @Transactional(readOnly = true)
    public List<Booking> getMyBookings(String username) {
        User user = userService.findByUsername(username);
        return bookingRepository.findByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Booking getBookingById(Long bookingId, String username) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (!booking.getUser().getUsername().equals(username)) {
            throw new SecurityException("Access denied to booking: " + bookingId);
        }
        return booking;
    }
}
