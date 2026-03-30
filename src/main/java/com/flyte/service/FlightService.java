package com.flyte.service;

import com.flyte.dto.FlightRequest;
import com.flyte.entity.Flight;
import com.flyte.exception.ResourceNotFoundException;
import com.flyte.repository.FlightRepository;
import com.flyte.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles all flight management operations.
 * Only ADMINs can create or delete flights.
 * Public (unauthenticated) users can search.
 */
@Service
@RequiredArgsConstructor
public class FlightService {

    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;

    /**
     * Create a new flight (ADMIN only).
     */
    public Flight addFlight(FlightRequest request) {
        if (flightRepository.existsByFlightNumber(request.getFlightNumber())) {
            throw new IllegalArgumentException("Flight number already exists: " + request.getFlightNumber());
        }

        Flight flight = Flight.builder()
                .flightNumber(request.getFlightNumber())
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .departureTime(request.getDepartureTime())
                .arrivalTime(request.getArrivalTime())
                .baseFare(request.getBaseFare())
                .availableSeats(request.getAvailableSeats())
                .build();

        return flightRepository.save(flight);
    }

    /**
     * Get all flights (public).
     */
    @Transactional(readOnly = true)
    public List<Flight> getAllFlights() {
        return flightRepository.findAll();
    }

    /**
     * Get a flight by ID (public).
     */
    @Transactional(readOnly = true)
    public Flight getFlightById(Long id) {
        return flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with ID: " + id));
    }

    /**
     * Get a flight by flight number (public).
     */
    @Transactional(readOnly = true)
    public Flight getFlightByNumber(String flightNumber) {
        return flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found: " + flightNumber));
    }

    /**
     * Search available flights by route and date range (public).
     */
    @Transactional(readOnly = true)
    public List<Flight> searchFlights(String origin, String destination,
                                      LocalDateTime from, LocalDateTime to) {
        return flightRepository.searchAvailableFlights(origin, destination, from, to);
    }

    /**
     * Delete a flight (ADMIN only).
     * Also deletes all related bookings first.
     */
    @Transactional
    public void deleteFlight(Long id) {
        Flight flight = getFlightById(id);

        // First delete bookings linked to this flight
        bookingRepository.deleteByFlight(flight);

        // Then delete the flight
        flightRepository.delete(flight);
    }
}
