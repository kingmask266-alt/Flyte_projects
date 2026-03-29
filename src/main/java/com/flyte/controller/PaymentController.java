package com.flyte.controller;

import com.flyte.dto.MpesaRequest;
import com.flyte.dto.MpesaStkResponse;
import com.flyte.entity.Payment;
import com.flyte.service.MpesaService;
import com.flyte.service.StripeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Payment endpoints for Mpesa and Stripe.
 *
 * PASSENGER:
 *   POST /api/payments/mpesa/pay           → trigger Mpesa STK Push
 *   POST /api/payments/stripe/intent/{id}  → get Stripe client secret
 *
 * PUBLIC (Safaricom callback):
 *   POST /api/payments/mpesa/callback      → Safaricom notifies us of payment result
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final MpesaService mpesaService;
    private final StripeService stripeService;

    @GetMapping("/stripe/config")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Map<String, String>> stripeConfig() {
        return ResponseEntity.ok(Map.of("publishableKey", stripeService.getPublishableKey()));
    }

    /**
     * Trigger an Mpesa STK Push to the customer's phone.
     * Customer will receive a prompt to enter their Mpesa PIN.
     */
    @PostMapping("/mpesa/pay")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<MpesaStkResponse> initiateMpesaPayment(@Valid @RequestBody MpesaRequest request) {
        MpesaStkResponse response = mpesaService.initiateStkPush(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Safaricom calls this URL automatically after the customer pays or declines.
     * This endpoint must be PUBLIC and reachable from the internet.
     */
    @PostMapping("/mpesa/callback")
    public ResponseEntity<String> mpesaCallback(@RequestBody Map<String, Object> payload) {
        mpesaService.handleCallback(payload);
        return ResponseEntity.ok("Callback received");
    }

    /**
     * Create a Stripe PaymentIntent and return the client_secret.
     * The frontend uses this to complete card payment via Stripe.js.
     */
    @PostMapping("/stripe/intent/{bookingId}")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Map<String, String>> createStripePaymentIntent(@PathVariable Long bookingId,
                                                                         @AuthenticationPrincipal UserDetails userDetails) {
        String clientSecret = stripeService.createPaymentIntent(bookingId, userDetails.getUsername());
        return ResponseEntity.ok(Map.of("clientSecret", clientSecret));
    }

    @PostMapping("/stripe/sync/{bookingId}")
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<Map<String, Object>> syncStripePayment(@PathVariable Long bookingId,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        Payment payment = stripeService.syncPaymentStatus(bookingId, userDetails.getUsername());
        return ResponseEntity.ok(Map.of(
                "status", payment.getStatus().name(),
                "transactionReference", payment.getTransactionReference()
        ));
    }

    /**
     * Stripe webhook — Stripe calls this when a payment is confirmed or fails.
     * Register this URL in your Stripe Dashboard under Webhooks.
     */
    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> stripeWebhook(@RequestBody Map<String, Object> payload) {
        String type = (String) payload.get("type");
        Map<?, ?> dataObject = (Map<?, ?>) ((Map<?, ?>) payload.get("data")).get("object");
        String paymentIntentId = (String) dataObject.get("id");

        boolean succeeded = "payment_intent.succeeded".equals(type);
        stripeService.confirmPayment(paymentIntentId, succeeded);

        return ResponseEntity.ok("Webhook processed");
    }
}
