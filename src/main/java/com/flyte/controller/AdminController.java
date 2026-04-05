package com.flyte.controller;

import com.flyte.service.BookingService;
import com.flyte.service.FlightService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

    @Autowired
    private FlightService flightService;

    @Autowired
    private BookingService bookingService;

    @GetMapping("/admin")
    public String adminDashboard(Model model) {
        model.addAttribute("flights", flightService.getAllFlights());
        model.addAttribute("bookings", bookingService.getAllBookings());
        model.addAttribute("totalFlights", flightService.getAllFlights().size());
        model.addAttribute("totalBookings", bookingService.getAllBookings().size());
        return "admin";
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/admin";
    }
}
