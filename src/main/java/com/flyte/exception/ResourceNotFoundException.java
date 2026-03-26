package com.flyte.exception;

/**
 * Thrown when a requested resource (Flight, Booking, User) does not exist in the database.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
