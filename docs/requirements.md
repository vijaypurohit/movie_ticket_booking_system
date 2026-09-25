# Movie Ticket Booking System 
Time limit: 48 hours. Stack: Spring Boot. 

## The Project 
A **movie ticket booking system** at scale with multiple cities, multiple theaters per city, multiple shows per theater, and seat-level booking. 
The system should support seat selection with timebound holds that release automatically on expiry, multiple pricing tiers (regular, premium, weekend) and discount codes, payment, booking confirmation, and refunds on cancellation under configurable refund policies.
Multiple users may attempt to book the same seat at the same time, and the system must correctly serialize bookings without double-allocation. 
Confirmation and reminder notifications should be delivered without blocking the booking flow. 

**Roles**: 
- admin (manage cities, theaters, shows, seat layouts, pricing tiers, and refund policies)
- customer (browse shows, book and cancel seats, view booking history). 

## Instructions 

### Framing 
The Product Requirement above is intentionally open-ended. You own the scoping decisions — which entities to model, which APIs to expose, which edge cases to handle, and what to leave out. Your interpretation of the requirement and the features you choose to build are themselves part of what is being evaluated. You are encouraged to interpret the requirement generously and submit a feature-rich solution — both the breadth and the depth of the features you build contribute to the evaluation. Document every meaningful assumption in the README.md and explain your reasoning in the recorded video. 

### In Scope 
The following are expected in your submission: 
- REST APIs covering the core flows 
- Persistence to a database of your choice 
- Basic role-based access control for the roles defined in the requirement 
- Input validation and error handling 
- Unit & Integration Tests for the core flows 

### Out of Scope 
Do not spend time on: 
- UI or frontend 
- Deployment, containerization, or CI/CD 
- Distributed systems or microservices 
- Advanced authentication (OAuth, SSO, MFA) 
- Production-grade observability, monitoring, or alerting 

## What to Submit 
- GitHub repository (mandatory) 
   - Your personal Github Project repository link 
   - Multiple commits are expected during the development phase 
   - Must include a README.md 
   - Must include the Agents.md / Claude.md file used during development Must include the skills used during development 
   - Must include all raw files used during development 

- Video recording (maximum 10 minutes) which you explain: 
   - How you approached the problem and the solution at a high level 
   - The tech stack used and the reasoning behind it 
   - The AI workflow used 
   - The testing approach 

