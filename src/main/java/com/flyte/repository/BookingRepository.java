package com.flyte.repository;

import com.flyte.entity.Booking;
import com.flyte.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByFlightId(Long flightId);

    boolean existsByFlightIdAndSeatNumber(Long flightId, String seatNumber);

    void deleteByFlight(Flight flight);
}