package com.flyte.controller;

import com.flyte.service.BookingService;
import com.flyte.service.FlightService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;
import java.util.List;

@Controller
public class AdminController {

    private final FlightService flightService;
    private final BookingService bookingService;

    public AdminController(FlightService flightService, BookingService bookingService) {
        this.flightService = flightService;
        this.bookingService = bookingService;
    }

    @GetMapping("/admin")
    public String adminDashboard(Model model) {
        List<?> flights;
        List<?> bookings;

        try {
            flights = flightService.getAllFlights();
            if (flights == null) flights = Collections.emptyList();
        } catch (Exception ex) {
            flights = Collections.emptyList();
            // optionally log the exception
        }

        try {
            bookings = bookingService.getAllBookings();
            if (bookings == null) bookings = Collections.emptyList();
        } catch (Exception ex) {
            bookings = Collections.emptyList();
            // optionally log the exception
        }

        model.addAttribute("flights", flights);
        model.addAttribute("bookings", bookings);
        model.addAttribute("totalFlights", flights.size());
        model.addAttribute("totalBookings", bookings.size());
        return "admin";
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/admin";
    }
}

