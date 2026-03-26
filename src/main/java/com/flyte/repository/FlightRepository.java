// =============================================
// FILE 1: FlightRepository.java
// =============================================
package com.flyte.repository;

import com.flyte.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightNumber(String flightNumber);

    boolean existsByFlightNumber(String flightNumber);

    List<Flight> findByOriginIgnoreCaseAndDestinationIgnoreCase(String origin, String destination);

    @Query("SELECT f FROM Flight f WHERE f.origin = :origin AND f.destination = :destination " +
           "AND f.departureTime >= :from AND f.departureTime <= :to AND f.availableSeats > 0")
    List<Flight> searchAvailableFlights(@Param("origin") String origin,
                                         @Param("destination") String destination,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);
}
