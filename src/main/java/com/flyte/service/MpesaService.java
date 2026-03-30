package com.flyte.service;

import com.flyte.dto.MpesaRequest;
import com.flyte.dto.MpesaStkResponse;
import com.flyte.entity.Booking;
import com.flyte.entity.Payment;
import com.flyte.entity.enums.PaymentMethod;
import com.flyte.entity.enums.PaymentStatus;
import com.flyte.exception.ResourceNotFoundException;
import com.flyte.repository.BookingRepository;
import com.flyte.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


/**
 * Integrates with Safaricom Daraja API for Mpesa STK Push payments.
 *
 * Flow:
 * 1. Get OAuth token from Safaricom
 * 2. Trigger STK Push to customer's phone
 * 3. Customer enters Mpesa PIN
 * 4. Safaricom calls our callback URL with result
 * 5. We update payment status in our DB
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MpesaService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${mpesa.consumer.key}")
    private String consumerKey;

    @Value("${mpesa.consumer.secret}")
    private String consumerSecret;

    @Value("${mpesa.passkey}")
    private String passkey;

    @Value("${mpesa.shortcode}")
    private String shortcode;

    @Value("${mpesa.callback.url}")
    private String callbackUrl;

    @Value("${mpesa.base.url}")
    private String mpesaBaseUrl;

    /**
     * Step 1: Get OAuth access token from Safaricom.
     */
    private String getAccessToken() {
        log.info("Attempting to get Mpesa access token from: {}", mpesaBaseUrl);
        log.info("Using consumer key: {}", consumerKey.substring(0, 5) + "...");

        String credentials = Base64.getEncoder()
                .encodeToString((consumerKey + ":" + consumerSecret)
                        .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

        try {
            Map<?, ?> response = webClientBuilder.build()
                    .get()
                    .uri(mpesaBaseUrl + "/oauth/v1/generate?grant_type=client_credentials")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .map(body -> {
                                    log.error("Safaricom token error - Status: {} Body: {}",
                                            clientResponse.statusCode(), body);
                                    return new RuntimeException("Token error: " + body);
                                });
                    })
                    .bodyToMono(Map.class)
                    .doOnError(error -> log.error("WebClient error getting token: {}", error.getMessage()))
                    .block();

            if (response == null || !response.containsKey("access_token")) {
                log.error("Token response was null or missing access_token: {}", response);
                throw new RuntimeException("Failed to retrieve Mpesa access token");
            }

            log.info("Successfully got Mpesa access token");
            return (String) response.get("access_token");

        } catch (Exception e) {
            log.error("Exception getting access token: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            throw new RuntimeException("Mpesa token fetch failed: " + e.getMessage(), e);
        }
    }

    /**
     * Step 2: Trigger STK Push to customer's phone.
     *
     * @param request contains bookingId and phone number
     */
    @Transactional
    public MpesaStkResponse initiateStkPush(MpesaRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + request.getBookingId()));

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        String password = Base64.getEncoder()
                .encodeToString((shortcode + passkey + timestamp).getBytes());

        String accessToken = getAccessToken();

        Map<String, String> body = new HashMap<>();
        body.put("BusinessShortCode", shortcode);
        body.put("Password", password);
        body.put("Timestamp", timestamp);
        body.put("TransactionType", "CustomerPayBillOnline");
        body.put("Amount", String.valueOf((int) booking.getPrice()));
        body.put("PartyA", request.getPhoneNumber());
        body.put("PartyB", shortcode);
        body.put("PhoneNumber", request.getPhoneNumber());
        body.put("CallBackURL", callbackUrl);
        body.put("AccountReference", "Flyte-" + booking.getId());
        body.put("TransactionDesc", "Flight booking payment");

        MpesaStkResponse stkResponse = webClientBuilder.build()
                .post()
                .uri(mpesaBaseUrl + "/mpesa/stkpush/v1/processrequest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MpesaStkResponse.class)
                .block();

        Optional<Payment> existingPayment = paymentRepository.findByBookingId(booking.getId());
        Payment payment = existingPayment.orElseGet(Payment::new);

        if (existingPayment.isPresent() && payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("This booking has already been paid.");
        }

        // Upsert payment record so retries do not violate one-to-one booking constraints.
        payment.setBooking(booking);
        payment.setAmount(booking.getPrice());
        payment.setPaymentMethod(PaymentMethod.MPESA);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionReference(stkResponse != null ? stkResponse.getCheckoutRequestId() : null);
        payment.setMpesaPhone(request.getPhoneNumber());

        paymentRepository.save(payment);
        log.info("Mpesa STK Push initiated for booking {} - phone {}", booking.getId(), request.getPhoneNumber());

        return stkResponse;
    }

    /**
     * Step 3: Handle Safaricom callback after customer pays.
     * Safaricom POSTs to /api/payments/mpesa/callback.
     */
    @Transactional
    public void handleCallback(Map<String, Object> callbackPayload) {
        try {
            Map<?, ?> body = (Map<?, ?>) callbackPayload.get("Body");
            Map<?, ?> stkCallback = (Map<?, ?>) body.get("stkCallback");
            int resultCode = (int) stkCallback.get("ResultCode");
            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");

            Payment payment = paymentRepository.findByTransactionReference(checkoutRequestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found for reference: " + checkoutRequestId));

            if (resultCode == 0) {
                payment.setStatus(PaymentStatus.SUCCESS);
                log.info("Mpesa payment SUCCESS for reference: {}", checkoutRequestId);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                log.warn("Mpesa payment FAILED for reference: {} - ResultCode: {}", checkoutRequestId, resultCode);
            }

            paymentRepository.save(payment);
        } catch (Exception e) {
            log.error("Error processing Mpesa callback: {}", e.getMessage());
        }
    }
}
