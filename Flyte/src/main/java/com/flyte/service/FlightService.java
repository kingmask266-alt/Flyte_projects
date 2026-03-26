package com.flyte.service;

import com.flyte.entity.Flight;
import com.flyte.repository.FlightRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FlightService {

    @Autowired
    private FlightRepository flightRepository;

    public List<Flight> getAllFlights() {
        return flightRepository.findAll();
    }

    public Optional<Flight> getFlightById(Long id) {
        return flightRepository.findById(id);
    }

    public Flight saveFlight(Flight flight) {
        return flightRepository.save(flight);
    }

    public void deleteFlight(Long id) {
        flightRepository.deleteById(id);
    }

    public Flight updateFlight(Long id, Flight updatedFlight) {
        return flightRepository.findById(id).map(flight -> {
            flight.setFlightNumber(updatedFlight.getFlightNumber());
            flight.setOrigin(updatedFlight.getOrigin());
            flight.setDestination(updatedFlight.getDestination());
            flight.setDepartureTime(updatedFlight.getDepartureTime());
            flight.setArrivalTime(updatedFlight.getArrivalTime());
            return flightRepository.save(flight);
        }).orElseThrow(() -> new RuntimeException("Flight not found with id: " + id));
    }
}
