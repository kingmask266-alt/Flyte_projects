package com.flyte.repository;

import com.flyte.entity.Booking;
import com.flyte.entity.Flight;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    long countByCancelledFalse();
    long countByCancelledTrue();

    @EntityGraph(attributePaths = {"flight", "user"})
    List<Booking> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"flight", "user"})
    Optional<Booking> findById(Long id);

    @EntityGraph(attributePaths = {"flight", "user"})
    List<Booking> findAll();

    List<Booking> findByFlightId(Long flightId);

    boolean existsByFlightIdAndSeatNumber(Long flightId, String seatNumber);

    void deleteByFlight(Flight flight);
}
