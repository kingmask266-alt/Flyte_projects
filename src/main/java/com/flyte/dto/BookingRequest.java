package com.flyte.dto;

import com.flyte.entity.enums.SeatClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingRequest {

    @NotBlank(message = "Flight number is required")
    private String flightNumber;

    @NotBlank(message = "Passenger name is required")
    private String passengerName;

    @NotNull(message = "Seat class is required")
    private SeatClass seatClass;

    @NotBlank(message = "Seat number is required")
    private String seatNumber;
}
