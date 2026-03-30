package com.flyte.controller;

import com.flyte.entity.Booking;
import com.flyte.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class ViewController {

    private final BookingService bookingService;

    @Value("${stripe.public.key:}")
    private String stripePublishableKey;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/payment/{bookingId}")
    @PreAuthorize("hasAuthority('ROLE_PASSENGER')")
    public String paymentPage(@PathVariable Long bookingId,
                              @AuthenticationPrincipal UserDetails userDetails,
                              Model model) {

        Booking booking = bookingService.getBookingById(bookingId, userDetails.getUsername());
        model.addAttribute("booking", booking);
        model.addAttribute("stripePublishableKey", stripePublishableKey);
        return "payment";
    }
}
