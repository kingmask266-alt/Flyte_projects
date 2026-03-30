package com.flyte.repository;

import com.flyte.entity.Payment;
import com.flyte.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByTransactionReference(String transactionReference);

    List<Payment> findByBookingUserId(Long userId);

    List<Payment> findByStatus(PaymentStatus status);
}
