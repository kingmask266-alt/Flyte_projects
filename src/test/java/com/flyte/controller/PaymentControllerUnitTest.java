package com.flyte.controller;

import com.flyte.dto.MpesaRequest;
import com.flyte.dto.MpesaStkResponse;
import com.flyte.entity.Booking;
import com.flyte.entity.Payment;
import com.flyte.entity.User;
import com.flyte.entity.enums.PaymentMethod;
import com.flyte.entity.enums.PaymentStatus;
import com.flyte.entity.enums.Role;
import com.flyte.service.MpesaService;
import com.flyte.service.StripeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
class PaymentControllerUnitTest {

    @Test
    void stripeConfigReturnsPublishableKey() {
        StubMpesaService mpesaService = new StubMpesaService();
        StubStripeService stripeService = new StubStripeService();
        PaymentController paymentController = new PaymentController(mpesaService, stripeService);
        stripeService.publishableKey = "pk_test_demo";

        ResponseEntity<Map<String, String>> response = paymentController.stripeConfig();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("pk_test_demo", response.getBody().get("publishableKey"));
    }

    @Test
    void initiateMpesaPaymentReturnsServiceResponse() {
        StubMpesaService mpesaService = new StubMpesaService();
        StubStripeService stripeService = new StubStripeService();
        PaymentController paymentController = new PaymentController(mpesaService, stripeService);

        MpesaRequest request = new MpesaRequest();
        request.setBookingId(21L);
        request.setPhoneNumber("254712345678");

        MpesaStkResponse stk = new MpesaStkResponse();
        stk.setCheckoutRequestId("ws_CO_123");
        stk.setResponseCode("0");

        mpesaService.nextStkResponse = stk;

        ResponseEntity<MpesaStkResponse> response = paymentController.initiateMpesaPayment(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ws_CO_123", response.getBody().getCheckoutRequestId());
    }

    @Test
    void createStripeIntentReturnsClientSecret() {
        StubMpesaService mpesaService = new StubMpesaService();
        StubStripeService stripeService = new StubStripeService();
        PaymentController paymentController = new PaymentController(mpesaService, stripeService);

        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("pax_demo")
                .password("x")
                .roles("PASSENGER")
                .build();
        stripeService.clientSecret = "pi_secret_abc";

        ResponseEntity<Map<String, String>> response = paymentController.createStripePaymentIntent(12L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("pi_secret_abc", response.getBody().get("clientSecret"));
    }

    @Test
    void syncStripeReturnsStatusAndReference() {
        StubMpesaService mpesaService = new StubMpesaService();
        StubStripeService stripeService = new StubStripeService();
        PaymentController paymentController = new PaymentController(mpesaService, stripeService);

        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("pax_demo")
                .password("x")
                .roles("PASSENGER")
                .build();

        Payment payment = Payment.builder()
                .id(3L)
                .booking(Booking.builder()
                        .id(12L)
                        .user(User.builder().id(9L).username("pax_demo").role(Role.PASSENGER).build())
                        .build())
                .amount(15000)
                .paymentMethod(PaymentMethod.STRIPE)
                .status(PaymentStatus.SUCCESS)
                .transactionReference("pi_12345")
                .build();

        stripeService.syncedPayment = payment;

        ResponseEntity<Map<String, Object>> response = paymentController.syncStripePayment(12L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertEquals("pi_12345", response.getBody().get("transactionReference"));
    }

    private static class StubMpesaService extends MpesaService {
        private MpesaStkResponse nextStkResponse;

        StubMpesaService() {
            super(null, null, null);
        }

        @Override
        public MpesaStkResponse initiateStkPush(MpesaRequest request) {
            return nextStkResponse;
        }
    }

    private static class StubStripeService extends StripeService {
        private String publishableKey = "";
        private String clientSecret = "";
        private Payment syncedPayment;

        StubStripeService() {
            super(null, null);
        }

        @Override
        public String getPublishableKey() {
            return publishableKey;
        }

        @Override
        public String createPaymentIntent(Long bookingId, String username) {
            return clientSecret;
        }

        @Override
        public Payment syncPaymentStatus(Long bookingId, String username) {
            return syncedPayment;
        }
    }
}
