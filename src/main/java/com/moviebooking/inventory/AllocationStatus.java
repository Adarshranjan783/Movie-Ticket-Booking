package com.moviebooking.inventory;

/** A seat allocation row exists only while a seat is taken; HELD rows expire, BOOKED rows live until cancellation. */
public enum AllocationStatus {
    HELD, BOOKED
}
