package com.flyte.controller;

import com.flyte.dto.FlightRequest;
import com.flyte.entity.Booking;
import com.flyte.entity.Flight;
import com.flyte.entity.Payment;
import com.flyte.entity.enums.PaymentStatus;
import com.flyte.repository.BookingRepository;
import com.flyte.repository.FlightRepository;
import com.flyte.repository.PaymentRepository;
import com.flyte.service.FlightService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class AdminController {

    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final FlightService flightService;

    public AdminController(FlightRepository flightRepository,
                           BookingRepository bookingRepository,
                           PaymentRepository paymentRepository,
                           FlightService flightService) {
        this.flightRepository = flightRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.flightService = flightService;
    }

    @GetMapping("/admin")
    public String adminPage(Model model) {
        if (!model.containsAttribute("flightForm")) {
            model.addAttribute("flightForm", new FlightRequest());
        }

        List<Flight> flights = flightRepository.findAll().stream()
                .sorted(Comparator.comparing(Flight::getDepartureTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<Booking> bookings = bookingRepository.findAll().stream()
                .sorted(Comparator.comparing(Booking::getBookedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Payment> payments = paymentRepository.findAll().stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<Long, Payment> paymentByBookingId = payments.stream()
                .filter(payment -> payment.getBooking() != null)
                .collect(Collectors.toMap(payment -> payment.getBooking().getId(), payment -> payment, (first, second) -> first));

        long totalFlights = flights.size();
        long totalBookings = bookings.size();
        long confirmedBookings = bookingRepository.countByCancelledFalse();
        long cancelledBookings = bookingRepository.countByCancelledTrue();
        long successfulPayments = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS).count();
        long pendingPayments = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.PENDING).count();
        double totalRevenue = bookings.stream()
                .filter(booking -> !booking.isCancelled())
                .mapToDouble(Booking::getPrice)
                .sum();
        double paidRevenue = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount)
                .sum();
        long flightsDepartingSoon = flights.stream()
                .filter(flight -> flight.getDepartureTime() != null && flight.getDepartureTime().isAfter(LocalDateTime.now()))
                .limit(3)
                .count();
        String busiestRoute = bookings.stream()
                .collect(Collectors.groupingBy(
                        booking -> booking.getFlight().getOrigin() + " -> " + booking.getFlight().getDestination(),
                        Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("No active route data");

        model.addAttribute("flights", flights);
        model.addAttribute("totalFlights", totalFlights);
        model.addAttribute("bookings", bookings);
        model.addAttribute("totalBookings", totalBookings);
        model.addAttribute("confirmedBookings", confirmedBookings);
        model.addAttribute("pendingBookings", 0L);
        model.addAttribute("cancelledBookings", cancelledBookings);
        model.addAttribute("successfulPayments", successfulPayments);
        model.addAttribute("pendingPayments", pendingPayments);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("paidRevenue", paidRevenue);
        model.addAttribute("flightsDepartingSoon", flightsDepartingSoon);
        model.addAttribute("busiestRoute", busiestRoute);
        model.addAttribute("paymentByBookingId", paymentByBookingId);
        model.addAttribute("recentPayments", payments.stream().limit(5).toList());
        model.addAttribute("upcomingFlights", flights.stream()
                .filter(flight -> flight.getDepartureTime() != null && flight.getDepartureTime().isAfter(LocalDateTime.now()))
                .limit(4)
                .toList());

        return "dashboard";
    }

    @PostMapping("/admin/flights")
    public String createFlight(@ModelAttribute("flightForm") FlightRequest flightForm,
                               RedirectAttributes redirectAttributes) {
        try {
            flightService.addFlight(flightForm);
            redirectAttributes.addFlashAttribute("successMessage", "Flight added successfully.");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("flightForm", flightForm);
        }

        return "redirect:/admin";
    }

    @PostMapping("/admin/flights/{id}/delete")
    public String deleteFlight(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            flightService.deleteFlight(id);
            redirectAttributes.addFlashAttribute("successMessage", "Flight removed successfully.");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        return "redirect:/admin";
    }
}
