package com.flyte.controller;

import com.flyte.entity.Flight;
import com.flyte.service.FlightService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
public class FlightController {

    @Autowired
    private FlightService flightService;

    // ─── List all flights ───────────────────────────────────────────
    @GetMapping("/flights")
    public String listFlights(Model model) {
        model.addAttribute("flights", flightService.getAllFlights());
        return "flights/list";
    }

    // ─── Show Add Flight form ───────────────────────────────────────
    @GetMapping("/flights/add")
    public String showAddForm(Model model) {
        model.addAttribute("flight", new Flight());
        return "flights/add";
    }

    // ─── Handle Add Flight form submission ──────────────────────────
    @PostMapping("/flights/add")
    public String addFlight(
            @RequestParam String flightNumber,
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrivalTime) {

        Flight flight = new Flight(flightNumber, origin, destination, departureTime, arrivalTime);
        flightService.saveFlight(flight);
        return "redirect:/flights";
    }

    // ─── Delete flight ──────────────────────────────────────────────
    @PostMapping("/flights/delete/{id}")
    public String deleteFlight(@PathVariable Long id) {
        flightService.deleteFlight(id);
        return "redirect:/flights";
    }
}
