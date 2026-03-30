package com.flyte.controller;

import com.flyte.dto.BookingRequest;
import com.flyte.entity.Booking;
import com.flyte.entity.Flight;
import com.flyte.entity.User;
import com.flyte.entity.enums.Role;
import com.flyte.entity.enums.SeatClass;
import com.flyte.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
class BookingControllerUnitTest {

    @Test
    void bookFlightReturnsCreatedResponse() {
        StubBookingService bookingService = new StubBookingService();
        BookingController bookingController = new BookingController(bookingService);

        BookingRequest request = new BookingRequest();
        request.setFlightNumber("FL-201");
        request.setPassengerName("pax_demo");
        request.setSeatClass(SeatClass.ECONOMY);
        request.setSeatNumber("A14");

        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("pax_demo")
                .password("x")
                .roles("PASSENGER")
                .build();

        Booking booking = sampleBooking(19L, "FL-201");
        bookingService.nextBookedFlight = booking;

        ResponseEntity<Booking> response = bookingController.bookFlight(request, principal);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(19L, response.getBody().getId());
        assertEquals("FL-201", response.getBody().getFlight().getFlightNumber());
    }

    @Test
    void myBookingsReturnsServiceList() {
        StubBookingService bookingService = new StubBookingService();
        BookingController bookingController = new BookingController(bookingService);

        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("pax_demo")
                .password("x")
                .roles("PASSENGER")
                .build();

        List<Booking> expected = List.of(sampleBooking(7L, "FL-202"));
        bookingService.myBookings = expected;

        ResponseEntity<List<Booking>> response = bookingController.getMyBookings(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(7L, response.getBody().get(0).getId());
    }

    private Booking sampleBooking(Long id, String flightNumber) {
        Flight flight = Flight.builder()
                .id(10L)
                .flightNumber(flightNumber)
                .origin("Nairobi")
                .destination("Mombasa")
                .availableSeats(20)
                .baseFare(15000)
                .build();

        User user = User.builder()
                .id(3L)
                .username("pax_demo")
                .email("pax_demo@flyte.local")
                .password("encoded")
                .role(Role.PASSENGER)
                .build();

        return Booking.builder()
                .id(id)
                .passengerName("pax_demo")
                .seatClass(SeatClass.ECONOMY)
                .seatNumber("A14")
                .price(15000)
                .flight(flight)
                .user(user)
                .cancelled(false)
                .build();
    }

    private static class StubBookingService extends BookingService {
        private Booking nextBookedFlight;
        private List<Booking> myBookings = List.of();

        StubBookingService() {
            super(null, null, null, null);
        }

        @Override
        public Booking bookFlight(BookingRequest request, String username) {
            return nextBookedFlight;
        }

        @Override
        public List<Booking> getMyBookings(String username) {
            return myBookings;
        }
    }
}
