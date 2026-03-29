package com.flyte.service;

import com.flyte.entity.Booking;
import com.flyte.entity.Payment;
import com.flyte.entity.enums.PaymentMethod;
import com.flyte.entity.enums.PaymentStatus;
import com.flyte.exception.ResourceNotFoundException;
import com.flyte.repository.BookingRepository;
import com.flyte.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Handles Stripe card payment processing.
 *
 * Flow:
 * 1. Create a PaymentIntent (backend)
 * 2. Return client_secret to frontend
 * 3. Frontend completes payment with Stripe.js
 * 4. Stripe calls webhook → we update payment status
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${stripe.public.key}")
    private String stripePublicKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    /**
     * Creates a Stripe PaymentIntent for a booking.
     * Returns the client_secret to be used by the frontend.
     *
     * @param bookingId the booking to pay for
     * @return Stripe client secret string
     */
    public String createPaymentIntent(Long bookingId) {
        return createPaymentIntent(bookingId, null);
    }

    public String createPaymentIntent(Long bookingId, String username) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (username != null && !booking.getUser().getUsername().equals(username)) {
            throw new SecurityException("You are not authorized to pay for this booking.");
        }

        try {
            // Stripe amounts are in smallest currency unit (cents for USD, or fill in for KES)
            long amountInCents = (long) (booking.getPrice() * 100);

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("kes") // Kenyan Shilling — change to "usd" if needed
                    .setDescription("Flyte booking #" + bookingId + " - " + booking.getPassengerName())
                    .putMetadata("bookingId", String.valueOf(bookingId))
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // Save pending payment record
            Payment payment = Payment.builder()
                    .booking(booking)
                    .amount(booking.getPrice())
                    .paymentMethod(PaymentMethod.STRIPE)
                    .status(PaymentStatus.PENDING)
                    .transactionReference(paymentIntent.getId())
                    .build();

            paymentRepository.save(payment);
            log.info("Stripe PaymentIntent created: {} for booking {}", paymentIntent.getId(), bookingId);

            return paymentIntent.getClientSecret();

        } catch (StripeException e) {
            log.error("Stripe error: {}", e.getMessage());
            throw new IllegalStateException("Failed to create Stripe payment: " + e.getMessage());
        }
    }

    public String getPublishableKey() {
        return stripePublicKey;
    }

    /**
     * Confirm a payment after Stripe webhook fires.
     * Update payment status in our database.
     *
     * @param paymentIntentId from Stripe webhook event
     * @param succeeded       true if payment succeeded, false if failed
     */
    public void confirmPayment(String paymentIntentId, boolean succeeded) {
        Payment payment = paymentRepository.findByTransactionReference(paymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentIntentId));

        payment.setStatus(succeeded ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        paymentRepository.save(payment);

        log.info("Stripe payment {} → status: {}", paymentIntentId, payment.getStatus());
    }

    public Payment syncPaymentStatus(Long bookingId, String username) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (!booking.getUser().getUsername().equals(username)) {
            throw new SecurityException("You are not authorized to verify this payment.");
        }

        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for booking: " + bookingId));

        if (payment.getPaymentMethod() != PaymentMethod.STRIPE || payment.getTransactionReference() == null) {
            throw new IllegalStateException("No Stripe payment intent exists for this booking.");
        }

        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(payment.getTransactionReference());
            boolean succeeded = "succeeded".equals(paymentIntent.getStatus());
            payment.setStatus(succeeded ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
            paymentRepository.save(payment);
            return payment;
        } catch (StripeException e) {
            log.error("Stripe sync error: {}", e.getMessage());
            throw new IllegalStateException("Failed to sync Stripe payment: " + e.getMessage());
        }
    }
}
