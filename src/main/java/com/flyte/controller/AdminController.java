package com.flyte.controller;

import com.flyte.repository.BookingRepository;
import com.flyte.repository.FlightRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;

    public AdminController(FlightRepository flightRepository, BookingRepository bookingRepository) {
        this.flightRepository = flightRepository;
        this.bookingRepository = bookingRepository;
    }

    @GetMapping("/admin")
    public String adminPage(Model model) {
        model.addAttribute("flights", flightRepository.findAll());
        model.addAttribute("totalFlights", flightRepository.count());

        model.addAttribute("bookings", bookingRepository.findAll());
        model.addAttribute("totalBookings", bookingRepository.count());

        model.addAttribute("confirmedBookings", bookingRepository.countByCancelledFalse());
        model.addAttribute("pendingBookings", 0L);
        model.addAttribute("cancelledBookings", bookingRepository.countByCancelledTrue());

        return "dashboard";
    }
}
