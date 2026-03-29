package com.flyte;

import com.flyte.entity.Flight;
import com.flyte.repository.FlightRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class FlightSeeder implements CommandLineRunner {

    private final FlightRepository flightRepository;

    public FlightSeeder(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    @Override
    public void run(String... args) {
        List<Flight> seedFlights = List.of(
                buildFlight("FL-201", "Nairobi", "Mombasa", 2, 7, 16500, 32),
                buildFlight("FL-202", "Mombasa", "Nairobi", 2, 12, 16250, 28),
                buildFlight("FL-301", "Nairobi", "Kisumu", 3, 8, 12400, 24),
                buildFlight("FL-302", "Kisumu", "Nairobi", 3, 15, 12100, 22),
                buildFlight("FL-401", "Nairobi", "Eldoret", 4, 9, 11800, 20),
                buildFlight("FL-501", "Nairobi", "Kigali", 5, 6, 28400, 18)
        );

        for (Flight flight : seedFlights) {
            if (!flightRepository.existsByFlightNumber(flight.getFlightNumber())) {
                flightRepository.save(flight);
            }
        }
    }

    private Flight buildFlight(String flightNumber,
                               String origin,
                               String destination,
                               int dayOffset,
                               int departureHour,
                               double baseFare,
                               int availableSeats) {
        LocalDateTime departure = LocalDateTime.now()
                .plusDays(dayOffset)
                .withHour(departureHour)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        return Flight.builder()
                .flightNumber(flightNumber)
                .origin(origin)
                .destination(destination)
                .departureTime(departure)
                .arrivalTime(departure.plusHours(1).plusMinutes(20))
                .baseFare(baseFare)
                .availableSeats(availableSeats)
                .build();
    }
}
