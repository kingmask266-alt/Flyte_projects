package com.flyte.controller;

import com.flyte.service.BookingService;
import com.flyte.service.FlightService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private FlightService flightService;

    // ─── List all bookings ──────────────────────────────────────────
    @GetMapping("/bookings")
    public String listBookings(Model model) {
        model.addAttribute("bookings", bookingService.getAllBookings());
        return "bookings/list";
    }

    // ─── Book a flight form ─────────────────────────────────────────
    @GetMapping("/book/{flightId}")
    public String showBookingForm(@PathVariable Long flightId, Model model) {
        flightService.getFlightById(flightId).ifPresent(f -> model.addAttribute("flight", f));
        return "bookings/book";
    }

    // ─── Handle booking form submission ─────────────────────────────
    @PostMapping("/book/{flightId}")
    public String createBooking(
            @PathVariable Long flightId,
            @RequestParam String passengerName) {
        bookingService.createBooking(passengerName, flightId);
        return "redirect:/bookings";
    }

    // ─── Update booking status ───────────────────────────────────────
    @PostMapping("/bookings/status/{id}")
    public String updateStatus(@PathVariable Long id, @RequestParam String status) {
        bookingService.updateStatus(id, status);
        return "redirect:/bookings";
    }

    // ─── Cancel / Delete booking ────────────────────────────────────
    @PostMapping("/bookings/delete/{id}")
    public String deleteBooking(@PathVariable Long id) {
        bookingService.deleteBooking(id);
        return "redirect:/bookings";
    }
}
