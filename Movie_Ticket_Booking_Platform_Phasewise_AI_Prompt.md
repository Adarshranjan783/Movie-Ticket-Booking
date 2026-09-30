**Movie Ticket Booking Platform\
Phase-by-Phase AI Development Plan**

*A single master document for building the Spring Boot backend
incrementally*

# Project Context

The project is a Movie Ticket Booking System built
with Spring Boot. It covers multiple cities, theaters, shows and
seat-level booking; time-bound seat holds; pricing tiers; discounts;
payment; booking confirmation; configurable refunds; concurrent booking
attempts; asynchronous confirmation/reminder notifications;
admin/customer roles; REST APIs; persistence; RBAC; validation; error
handling; and unit/integration tests. The specification also states that
UI, deployment/containerization/CI-CD, distributed
systems/microservices, advanced authentication, and production-grade
observability are out of scope.

# How to Use This Document

-   Give the Master Prompt to the AI coding assistant at the beginning
    of the project.

-   Then execute Phase 0 through Phase 11 sequentially.

-   Do not ask the AI to implement future phases prematurely.

-   At every phase, let the AI inspect the existing repository before
    modifying it.

-   Keep the existing implementation intact unless a justified refactor
    is needed.

-   After each phase, review the generated code, run tests, and create
    the suggested professional commit.

-   Use the Final Review and Demo prompts only after the implementation
    is complete.

# MASTER PROMPT

> You are a senior backend engineer helping me build a
> production-quality Movie Ticket Booking Platform using Spring Boot.\
> \
> Treat this as a real-world backend product development project.\
> \
> The system needs to support:\
> \
> - Multiple cities\
> - Multiple theaters per city\
> - Multiple screens per theater\
> - Movies and multiple shows\
> - Screen-specific seat layouts\
> - Seat-level booking\
> - Time-bound seat holds with automatic expiry\
> - Multiple pricing tiers such as regular, premium and weekend\
> - Discount/coupon codes\
> - Payment processing through an abstraction\
> - Booking confirmation\
> - Cancellation\
> - Configurable refund policies\
> - Refund processing\
> - Asynchronous confirmation/reminder notifications\
> - Concurrent users attempting to book the same seat\
> - Correct serialization so that a seat can never be double-allocated\
> - Admin and customer roles\
> - REST APIs\
> - Database persistence\
> - Input validation\
> - Consistent error handling\
> - Unit tests\
> - Integration tests\
> \
> Technology:\
> \
> - Java\
> - Spring Boot\
> - Spring Data JPA\
> - Spring Security\
> - PostgreSQL\
> - JUnit 5\
> - Mockito\
> - Maven\
> \
> Architecture:\
> \
> Use a modular monolith architecture.\
> \
> Do NOT introduce microservices, Kubernetes, Kafka, OAuth/SSO/MFA,
> frontend, CI/CD or deployment infrastructure unless there is a strong
> technical reason and I explicitly ask for it.\
> \
> Prioritize correctness, clean architecture, maintainability and strong
> backend engineering.\
> \
> Important engineering principles:\
> \
> 1. Keep controllers thin.\
> 2. Put business logic in services/domain-oriented components.\
> 3. Use DTOs instead of exposing entities directly.\
> 4. Define clear transaction boundaries.\
> 5. Use database constraints where they provide correctness
> guarantees.\
> 6. Handle concurrency explicitly.\
> 7. Design booking and payment operations to be idempotent where
> required.\
> 8. Use explicit state transitions instead of scattered boolean flags.\
> 9. Use consistent API response/error structures.\
> 10. Validate input at both API and business layers where appropriate.\
> 11. Write tests alongside important functionality.\
> 12. Avoid unnecessary abstraction and over-engineering.\
> 13. Document meaningful architectural decisions and trade-offs.\
> 14. Keep the implementation realistic for a senior backend engineer.\
> 15. Every feature should be production-quality within the defined
> scope.\
> \
> Development style:\
> \
> - Work incrementally.\
> - Before implementing a major feature, explain the design briefly.\
> - Identify important edge cases before coding.\
> - Reuse existing code rather than unnecessarily creating new
> abstractions.\
> - Do not rewrite working code without a clear reason.\
> - Do not introduce dependencies unless necessary.\
> - Maintain backward compatibility with previously implemented APIs
> unless a change is required.\
> - After implementation, review the code for correctness, race
> conditions, transaction issues, validation problems and
> maintainability.\
> - Add appropriate unit/integration tests for each important feature.\
> - Keep the Git history natural and professional with feature-oriented
> commits.\
> \
> Very important:\
> \
> Use normal product and engineering terminology in code, comments,
> commit messages, README text and architecture documentation.\
> \
> The project should look and feel like a genuine backend product
> developed by an experienced engineer.\
> \
> I will give you the development phases one by one.\
> \
> For each phase:\
> \
> 1. Understand the existing project before changing anything.\
> 2. Explain what we are implementing.\
> 3. Explain important design decisions.\
> 4. Identify dependencies on existing functionality.\
> 5. Implement the feature completely.\
> 6. Add/update tests.\
> 7. Check for regressions.\
> 8. Review the implementation.\
> 9. Suggest a professional Git commit message.\
> 10. Clearly state what is now complete and what should be done next.\
> \
> Do not jump ahead into future phases unless required by the current
> implementation.

# Phase 0 --- Product Foundation

> PHASE 0 --- PRODUCT FOUNDATION\
> \
> Start by establishing the technical foundation of the Movie Ticket
> Booking Platform.\
> \
> Do not implement the complete business functionality yet.\
> \
> First inspect the current repository/project structure and determine
> what already exists.\
> \
> Then design and establish:\
> \
> 1. Overall modular monolith architecture\
> 2. Package structure\
> 3. Core domain boundaries\
> 4. Database strategy\
> 5. Entity relationship model\
> 6. Major business states\
> 7. API organization\
> 8. Exception handling strategy\
> 9. Validation strategy\
> 10. Security/RBAC foundation\
> 11. Testing structure\
> 12. Configuration strategy\
> \
> Identify the core domain concepts we will need, including where
> appropriate:\
> \
> - User\
> - Role\
> - City\
> - Theater\
> - Screen\
> - Seat\
> - Movie\
> - Show\
> - Booking\
> - BookingSeat\
> - SeatHold\
> - Payment\
> - Coupon/Discount\
> - RefundPolicy\
> - Refund\
> - Notification\
> \
> Do not blindly create every entity immediately if the domain model can
> be improved.\
> \
> For the important entities, define:\
> \
> - Responsibilities\
> - Relationships\
> - Lifecycle\
> - Important fields\
> - Constraints\
> - Indexes\
> - Unique constraints\
> \
> Pay particular attention to:\
> \
> City → Theater → Screen → Seat\
> \
> and:\
> \
> Movie → Show → Screen\
> \
> and:\
> \
> Show → Seat availability → Hold → Booking\
> \
> Before coding, explain the proposed architecture and database model.\
> \
> Then implement only the foundational project structure/configuration
> that is necessary.\
> \
> Do not implement the full booking workflow yet.\
> \
> Add basic tests proving that the application starts and the
> persistence layer is correctly configured.\
> \
> At the end:\
> \
> - Review the project structure.\
> - Identify any architectural concerns.\
> - Provide the Git commit message.\
> - Summarize what is ready for the next phase.

# Phase 1 --- Domain & Persistence

> PHASE 1 --- DOMAIN AND PERSISTENCE\
> \
> Now implement the core domain model and persistence layer.\
> \
> First inspect everything currently implemented.\
> \
> Build the database model for the movie booking platform.\
> \
> Implement the necessary entities, repositories, migrations/schema and
> relationships.\
> \
> Pay special attention to:\
> \
> - Primary keys\
> - Foreign keys\
> - Unique constraints\
> - Indexes\
> - NOT NULL constraints\
> - Enum/state representation\
> - Created/updated timestamps\
> - Referential integrity\
> - Soft delete vs hard delete where appropriate\
> \
> The core hierarchy should support:\
> \
> City\
> → Theater\
> → Screen\
> → Seat\
> \
> Movie\
> → Show\
> → Screen\
> \
> User\
> → Booking\
> → BookingSeat\
> \
> Also prepare the persistence model required for:\
> \
> - Seat holds\
> - Payments\
> - Coupons\
> - Refund policies\
> - Refunds\
> - Notifications\
> \
> Do not implement unnecessary business workflows yet.\
> \
> However, the persistence model must be designed so that future
> seat-hold and booking concurrency can be implemented safely.\
> \
> Think carefully about how we will guarantee that the same seat for the
> same show cannot be successfully allocated to multiple bookings.\
> \
> Do NOT solve concurrency only at the Java level. Evaluate the role of
> database constraints and transactions.\
> \
> Implement repositories and persistence tests.\
> \
> Add realistic seed/test data only if useful for development.\
> \
> Do not create fake production data unnecessarily.\
> \
> At the end, review:\
> \
> - Entity relationships\
> - Database constraints\
> - Indexes\
> - JPA mappings\
> - Potential N+1 issues\
> - Lazy/eager loading choices\
> - Transaction implications\
> \
> Provide a professional Git commit message.

# Phase 2 --- Admin Configuration

> PHASE 2 --- ADMIN CONFIGURATION\
> \
> Implement the administrative configuration capabilities required by
> the platform.\
> \
> An administrator should be able to manage:\
> \
> 1. Cities\
> 2. Theaters\
> 3. Screens\
> 4. Seat layouts\
> 5. Movies\
> 6. Shows\
> 7. Pricing tiers\
> 8. Refund policies\
> \
> First inspect the existing domain and persistence implementation.\
> \
> Design clean REST APIs using DTOs.\
> \
> Controllers must remain thin.\
> \
> Implement:\
> \
> - Request DTOs\
> - Response DTOs\
> - Validation\
> - Services\
> - Repositories where required\
> - Exception handling\
> - Authorization checks\
> - Integration tests\
> \
> Ensure business invariants such as:\
> \
> - Theater must belong to a valid city.\
> - Screen must belong to a valid theater.\
> - Seat belongs to exactly one screen.\
> - Shows reference valid movies and screens.\
> - A show cannot have invalid start/end times.\
> - Conflicting shows on the same screen should be handled
> appropriately.\
> - Pricing configuration must be valid.\
> - Refund policies must be internally consistent.\
> \
> Do not allow customers to access admin operations.\
> \
> Use explicit authorization rules.\
> \
> Keep the APIs consistent and RESTful.\
> \
> Add integration tests for the main admin workflows.\
> \
> Do not implement customer booking yet.\
> \
> At the end, review the API design and provide professional Git commit
> messages for the completed feature groups.

# Phase 3 --- Movie & Show Discovery

> PHASE 3 --- CUSTOMER DISCOVERY\
> \
> Now implement the customer-facing discovery/read workflows.\
> \
> Customers should be able to:\
> \
> - Browse cities\
> - Browse movies\
> - Browse theaters\
> - Find shows\
> - View show details\
> - View the seat layout and current availability\
> \
> Design the APIs around a natural user journey:\
> \
> City\
> → Movie\
> → Theater\
> → Show\
> → Seat layout\
> \
> Implement:\
> \
> - Filtering where appropriate\
> - Pagination where useful\
> - DTOs\
> - Validation\
> - Authorization\
> - Efficient database queries\
> - Proper indexing\
> - Integration tests\
> \
> Do not introduce unnecessary search infrastructure.\
> \
> The read APIs should be efficient and should not expose JPA entities
> directly.\
> \
> For seat availability, clearly distinguish at least:\
> \
> AVAILABLE\
> HELD\
> BOOKED\
> \
> The availability response should contain enough information for a
> customer to understand:\
> \
> - Seat identifier\
> - Row\
> - Number\
> - Category/tier\
> - Current availability\
> - Price if appropriate\
> \
> Do not implement actual seat booking yet.\
> \
> However, ensure the design is compatible with the upcoming concurrent
> seat-hold implementation.\
> \
> Review queries for N+1 problems.\
> \
> Add integration tests covering the customer discovery flow.\
> \
> Finish with a code review and professional Git commit message.

# Phase 4 --- Seat Hold & Concurrency

> PHASE 4 --- SEAT INVENTORY, TEMPORARY HOLDS AND CONCURRENCY\
> \
> Now implement the seat inventory and temporary seat-hold system.\
> \
> This is a critical part of the platform.\
> \
> Multiple customers may attempt to select the same seat for the same
> show at approximately the same time.\
> \
> The system MUST guarantee that the same seat cannot be successfully
> allocated to multiple users.\
> \
> Design and implement:\
> \
> 1. Seat availability checking\
> 2. Temporary seat holds\
> 3. Hold expiration\
> 4. Automatic release of expired holds\
> 5. Concurrent hold protection\
> 6. Transaction boundaries\
> 7. Database-level correctness guarantees\
> 8. Proper failure handling\
> \
> Example:\
> \
> User A → Seat A10 → HOLD SUCCESS\
> \
> User B → Seat A10 → HOLD FAILURE\
> \
> If User A does not complete the booking within the configured hold
> duration:\
> \
> Seat A10 → HOLD EXPIRED → AVAILABLE\
> \
> Think deeply about the concurrency model.\
> \
> Before implementing, explicitly explain:\
> \
> - What resource is being locked?\
> - Where is the lock applied?\
> - Pessimistic vs optimistic locking considerations\
> - What database constraint protects against duplicate allocation?\
> - What happens when two transactions race?\
> - What happens if a hold expires while payment is being processed?\
> - What happens if a request is retried?\
> - What happens if a user requests multiple seats and one cannot be
> acquired?\
> - How are partial failures prevented?\
> \
> Prefer correctness over cleverness.\
> \
> Use database transactions appropriately.\
> \
> Do not rely solely on synchronized Java methods because the
> application may have multiple threads and the correctness guarantee
> should be at the persistence level.\
> \
> Implement an appropriate expiration mechanism.\
> \
> Add strong tests for:\
> \
> - Successful hold\
> - Duplicate hold\
> - Expired hold\
> - Hold release\
> - Multiple-seat hold\
> - Partial availability\
> - Concurrent requests for the same seat\
> - Concurrent requests for overlapping seat sets\
> - Retry behavior\
> \
> Where possible, create a concurrency integration test that actually
> executes competing requests/transactions.\
> \
> After implementation, perform a dedicated race-condition review.\
> \
> This phase must be robust enough to explain in a senior backend
> interview.\
> \
> Provide professional Git commit messages.

# Phase 5 --- Pricing & Booking

> PHASE 5 --- PRICING AND BOOKING WORKFLOW\
> \
> Now implement the complete booking workflow on top of the existing
> seat-hold system.\
> \
> The flow should be approximately:\
> \
> Seat selection\
> → Temporary hold\
> → Price calculation\
> → Discount application\
> → Booking creation\
> → Payment initiation\
> → Payment success\
> → Booking confirmation\
> \
> Implement a dedicated pricing component/service.\
> \
> Support pricing tiers such as:\
> \
> - REGULAR\
> - PREMIUM\
> - WEEKEND\
> \
> Support discount/coupon codes.\
> \
> Pricing should be deterministic and testable.\
> \
> Do not put pricing calculations inside controllers.\
> \
> Design clear responsibilities for:\
> \
> - Pricing calculation\
> - Coupon validation\
> - Discount calculation\
> - Booking creation\
> - Payment initiation\
> - Booking confirmation\
> \
> Introduce explicit booking states.\
> \
> Avoid scattered boolean flags such as:\
> \
> isPaid\
> isCancelled\
> isConfirmed\
> \
> Instead use an explicit lifecycle/state model.\
> \
> Implement a payment abstraction rather than integrating a real
> external payment provider.\
> \
> The payment abstraction should support at least:\
> \
> - Initiate payment\
> - Success\
> - Failure\
> - Idempotency\
> \
> Think carefully about idempotency.\
> \
> If the same payment request is submitted twice, it must not create
> duplicate payment side effects.\
> \
> Implement appropriate idempotency keys/records.\
> \
> Define transaction boundaries carefully.\
> \
> Explain what should happen inside the database transaction and what
> should happen outside it.\
> \
> Handle failures such as:\
> \
> - Invalid hold\
> - Expired hold\
> - Seat no longer available\
> - Invalid coupon\
> - Payment failure\
> - Duplicate payment request\
> - Booking retry\
> - Database transaction failure\
> \
> Add unit tests for pricing and discount logic.\
> \
> Add integration tests for the end-to-end booking flow.\
> \
> Add tests for payment idempotency.\
> \
> Review the implementation for race conditions and inconsistent
> states.\
> \
> Provide professional Git commit messages.

# Phase 6 --- Cancellation & Refunds

> PHASE 6 --- CANCELLATION AND REFUNDS\
> \
> Implement the booking cancellation and refund lifecycle.\
> \
> A customer should be able to cancel an eligible booking.\
> \
> The cancellation workflow should:\
> \
> 1. Validate the booking\
> 2. Verify ownership\
> 3. Determine whether cancellation is allowed\
> 4. Load the applicable refund policy\
> 5. Calculate the refund\
> 6. Update booking state\
> 7. Release the appropriate seats\
> 8. Initiate the refund\
> 9. Record refund state\
> 10. Trigger the appropriate notification/event\
> \
> Refund rules must be configurable rather than hardcoded.\
> \
> Design a RefundPolicyService that determines the refund amount based
> on the configured policy and cancellation timing.\
> \
> Support scenarios such as:\
> \
> - Full refund\
> - Partial refund\
> - No refund\
> - Already cancelled booking\
> - Already refunded booking\
> - Cancellation after the allowed window\
> - Payment that cannot be refunded immediately\
> \
> Make cancellation idempotent where appropriate.\
> \
> Think carefully about transaction boundaries.\
> \
> For example, avoid ending up in a state where:\
> \
> Booking = CANCELLED\
> \
> but:\
> \
> Seats = BOOKED\
> \
> or:\
> \
> Booking = CONFIRMED\
> \
> but:\
> \
> Refund = SUCCESS\
> \
> without the corresponding state transition being recorded.\
> \
> Add unit tests for refund calculation.\
> \
> Add integration tests for:\
> \
> - Successful cancellation\
> - Full refund\
> - Partial refund\
> - No refund\
> - Duplicate cancellation\
> - Invalid cancellation\
> - Seat release\
> - Refund failure/retry\
> \
> Review the complete booking lifecycle after implementation.\
> \
> Provide professional Git commit messages.

# Phase 7 --- Notifications

> PHASE 7 --- ASYNCHRONOUS NOTIFICATIONS\
> \
> Implement the notification workflow without blocking the core booking
> operation.\
> \
> The platform should support notifications for events such as:\
> \
> - Booking confirmation\
> - Booking cancellation\
> - Refund status\
> - Upcoming show reminder\
> \
> The booking API should NOT wait for notification delivery to
> complete.\
> \
> Design an event-driven internal flow such as:\
> \
> Business operation\
> → Domain/application event\
> → Asynchronous notification handler\
> → Notification delivery\
> \
> Use a simple in-process asynchronous mechanism unless the existing
> architecture genuinely requires something more complex.\
> \
> Do not introduce Kafka or another distributed messaging system just
> for the sake of the feature.\
> \
> Implement:\
> \
> - Notification event model\
> - Notification service\
> - Asynchronous processing\
> - Notification status\
> - Failure handling\
> - Retry behavior where appropriate\
> \
> Make sure failures in notification delivery do not roll back a
> successful booking.\
> \
> Add tests proving that the booking flow is independent of notification
> delivery.\
> \
> Also implement the reminder workflow for upcoming shows if practical
> within the existing architecture.\
> \
> Review thread-safety and transaction/event boundaries.\
> \
> Provide professional Git commit messages.

# Phase 8 --- Security & API Hardening

> PHASE 8 --- SECURITY, VALIDATION AND API HARDENING\
> \
> Now harden the entire API surface.\
> \
> Implement basic role-based access control for:\
> \
> ADMIN\
> CUSTOMER\
> \
> Ensure:\
> \
> ADMIN-only endpoints cannot be accessed by customers.\
> \
> Customers must only be able to access their own booking history and
> bookings.\
> \
> Review authorization at the service layer where ownership matters.\
> \
> Implement consistent request validation.\
> \
> Review:\
> \
> - Null values\
> - Empty strings\
> - Invalid IDs\
> - Invalid dates\
> - Invalid state transitions\
> - Invalid seat selections\
> - Invalid coupon codes\
> - Invalid pricing\
> - Invalid cancellation requests\
> \
> Create a centralized exception handling mechanism.\
> \
> Define consistent API error responses containing appropriate
> information such as:\
> \
> - Error code\
> - Human-readable message\
> - Timestamp\
> - Request/correlation identifier if already supported\
> \
> Do not expose internal stack traces or database details.\
> \
> Review all APIs for:\
> \
> - Authentication/authorization\
> - Input validation\
> - Object ownership\
> - IDOR risks\
> - Transaction correctness\
> - Error handling\
> - Sensitive data exposure\
> \
> Do not introduce advanced authentication mechanisms that are
> unnecessary for the current scope.\
> \
> Add integration tests for role-based access control and invalid
> requests.\
> \
> Perform a full API consistency review.\
> \
> Provide professional Git commit messages.

# Phase 9 --- Complete Testing

> PHASE 9 --- TESTING AND RELIABILITY\
> \
> Now perform a dedicated testing pass across the entire platform.\
> \
> Do not simply increase code coverage.\
> \
> Focus on business-critical behavior.\
> \
> Create/complete unit tests for:\
> \
> - Pricing\
> - Discounts\
> - Coupons\
> - Refund calculation\
> - Booking state transitions\
> - Seat hold logic\
> - Validation logic\
> \
> Create integration tests for:\
> \
> 1. Admin creates city → theater → screen → seats\
> 2. Admin creates movie and show\
> 3. Customer discovers shows\
> 4. Customer views seats\
> 5. Customer holds seats\
> 6. Customer completes booking\
> 7. Payment succeeds\
> 8. Booking becomes confirmed\
> 9. Booking cancellation\
> 10. Refund\
> 11. Seat release\
> 12. Notification triggering\
> \
> Most importantly, test concurrency.\
> \
> Create a realistic test where multiple concurrent requests attempt to
> hold/book the same seat.\
> \
> The expected invariant is:\
> \
> For a particular show and seat, at most one successful active
> allocation exists.\
> \
> Also test:\
> \
> - Hold expiration\
> - Payment retry\
> - Duplicate requests\
> - Duplicate cancellation\
> - Invalid state transitions\
> - Partial multi-seat failures\
> - Database constraint violations\
> - Notification failure\
> \
> Review flaky tests and remove timing assumptions where possible.\
> \
> After tests pass, inspect the code for:\
> \
> - Race conditions\
> - N+1 queries\
> - Missing indexes\
> - Incorrect transactions\
> - Unnecessary database calls\
> - Poor exception handling\
> - Over-engineering\
> - Dead code\
> \
> Do not add tests solely for superficial coverage.\
> \
> Focus on business correctness.

# Phase 10 --- Final Engineering Review

> PHASE 10 --- FINAL ENGINEERING REVIEW\
> \
> Treat the current project as a codebase that is about to be reviewed
> by a senior backend engineering panel.\
> \
> Perform a complete review of the existing implementation.\
> \
> Review:\
> \
> 1. Architecture\
> 2. Domain model\
> 3. Database design\
> 4. API design\
> 5. Transaction boundaries\
> 6. Concurrency\
> 7. Seat allocation correctness\
> 8. Hold expiration\
> 9. Booking state machine\
> 10. Payment idempotency\
> 11. Cancellation\
> 12. Refund consistency\
> 13. Notification architecture\
> 14. RBAC\
> 15. Validation\
> 16. Error handling\
> 17. Testing\
> 18. Code quality\
> 19. Query efficiency\
> 20. Maintainability\
> \
> Do not rewrite working code unnecessarily.\
> \
> For every issue found:\
> \
> - Explain why it matters.\
> - Classify its severity.\
> - Propose the smallest appropriate improvement.\
> - Implement the improvement only when justified.\
> \
> Pay particular attention to invariants such as:\
> \
> - A seat cannot be double-booked for the same show.\
> - Expired holds cannot be used for booking.\
> - A booking cannot transition through invalid states.\
> - Payment retries cannot create duplicate payment side effects.\
> - Customers cannot access another customer\'s bookings.\
> - Cancellation cannot incorrectly release another booking\'s seats.\
> - Refund state remains consistent with booking/payment state.\
> - Notification failure does not break booking confirmation.\
> \
> After the review, run the complete test suite.\
> \
> Then provide:\
> \
> - Final architecture summary\
> - Important design decisions\
> - Important trade-offs\
> - Known limitations\
> - Potential future improvements\
> - Final Git commit message

# Phase 11 --- README & Developer Documentation

> PHASE 11 --- PROJECT DOCUMENTATION\
> \
> Now create polished developer documentation for the Movie Ticket
> Booking Platform.\
> \
> The documentation should look like documentation for a real backend
> product.\
> \
> Create/update README.md with:\
> \
> 1. Project overview\
> 2. Core capabilities\
> 3. Technology stack\
> 4. Architecture\
> 5. Package structure\
> 6. Domain model\
> 7. Database design\
> 8. API overview\
> 9. Authentication and authorization\
> 10. Booking lifecycle\
> 11. Seat-hold lifecycle\
> 12. Concurrency strategy\
> 13. Pricing system\
> 14. Discount system\
> 15. Payment abstraction\
> 16. Cancellation and refund model\
> 17. Notification architecture\
> 18. Testing strategy\
> 19. How to run locally\
> 20. Configuration/environment variables\
> 21. Example API workflows\
> 22. Important assumptions\
> 23. Architectural trade-offs\
> 24. Known limitations\
> 25. Future improvements\
> \
> Document WHY important decisions were made, not just WHAT was
> implemented.\
> \
> In particular explain:\
> \
> - Why a modular monolith was chosen\
> - How double booking is prevented\
> - Why transactions are placed where they are\
> - How temporary holds expire\
> - How payment idempotency works\
> - How cancellation/refunds work\
> - Why notifications are asynchronous\
> - Why certain technologies were intentionally not introduced\
> \
> Use professional product and engineering terminology throughout the
> documentation.\
> \
> Use professional product/project terminology throughout.


# Final Development Checklist

-   [ ] Spring Boot modular monolith is cleanly structured.

-   [ ] Database schema and relationships are correct and indexed.

-   [ ] Admin configuration workflows are complete.

-   [ ] Customer discovery workflows are complete.

-   [ ] Seat availability is correct.

-   [ ] Temporary seat holds expire correctly.

-   [ ] Concurrent seat requests cannot double-allocate a seat.

-   [ ] Pricing tiers and discounts are implemented.

-   [ ] Booking lifecycle is explicit and validated.

-   [ ] Payment abstraction and idempotency are implemented.

-   [ ] Cancellation and configurable refunds are implemented.

-   [ ] Notifications do not block booking confirmation.

-   [ ] RBAC and ownership checks are enforced.

-   [ ] Validation and centralized error handling are complete.

-   [ ] Unit and integration tests cover core flows.

-   [ ] Concurrency tests cover the critical seat-allocation invariant.

-   [ ] README explains architecture, assumptions, decisions and
    trade-offs.

-   [ ] Git history contains natural feature-oriented commits.
