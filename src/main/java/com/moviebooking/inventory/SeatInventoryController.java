package com.moviebooking.inventory;

import com.moviebooking.inventory.InventoryDtos.*;
import com.moviebooking.user.AppUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SeatInventoryController {

    private final SeatHoldService holds;

    public SeatInventoryController(SeatHoldService holds) {
        this.holds = holds;
    }

    /** Public: seat layout with live availability for a show. */
    @GetMapping("/shows/{showId}/seats")
    public SeatMapResponse seatMap(@PathVariable Long showId) {
        return holds.seatMap(showId);
    }

    /** Customer: temporarily hold seats before booking. */
    @PostMapping("/shows/{showId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse hold(@PathVariable Long showId,
                             @Valid @RequestBody HoldRequest req,
                             @AuthenticationPrincipal AppUserDetails user) {
        return holds.hold(showId, req.seatIds(), user.getId());
    }

    /** Customer: give the seats back early. */
    @DeleteMapping("/holds/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable String holdId, @AuthenticationPrincipal AppUserDetails user) {
        holds.release(holdId, user.getId());
    }
}
