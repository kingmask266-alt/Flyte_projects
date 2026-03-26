package com.flyte.controller;

import com.flyte.dto.BookingRequest;
import com.flyte.entity.Booking;
import com.flyte.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Booking management endpoints.
 *
 * CUSTOMER:
 *   POST   /api/bookings           → book a flight
 *   DELETE /api/bookings/{id}      → cancel own booking
 *   GET    /api/bookings/my        → view own bookings
 *
 * ADMIN:
 *   GET    /api/bookings/all       → view all bookings
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Booking> bookFlight(@Valid @RequestBody BookingRequest request,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        Booking booking = bookingService.bookFlight(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Booking> cancelBooking(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        Booking booking = bookingService.cancelBooking(id, userDetails.getUsername());
        return ResponseEntity.ok(booking);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<List<Booking>> getMyBookings(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(bookingService.getMyBookings(userDetails.getUsername()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Booking>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }
}
