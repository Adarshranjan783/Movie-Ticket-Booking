package com.moviebooking.booking;

import com.moviebooking.booking.BookingDtos.*;
import com.moviebooking.user.AppUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest req,
                                  @AuthenticationPrincipal AppUserDetails user) {
        return bookingService.create(req, user.getId());
    }

    @PostMapping("/{bookingId}/pay")
    public BookingResponse pay(@PathVariable Long bookingId,
                               @Valid @RequestBody PayRequest req,
                               @AuthenticationPrincipal AppUserDetails user) {
        return bookingService.pay(bookingId, req, user.getId());
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable Long bookingId, @AuthenticationPrincipal AppUserDetails user) {
        return bookingService.cancel(bookingId, user.getId());
    }

    @GetMapping
    public List<BookingResponse> mine(@AuthenticationPrincipal AppUserDetails user) {
        return bookingService.listMine(user.getId());
    }

    @GetMapping("/{bookingId}")
    public BookingResponse get(@PathVariable Long bookingId, @AuthenticationPrincipal AppUserDetails user) {
        return bookingService.get(bookingId, user.getId());
    }
}
