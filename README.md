# ✈ Flyte — Flight Booking System
A collaborative flight booking system project

Overview
Flyte is a group project built to simulate a modern airline booking platform. It provides passengers with a seamless experience to search flights, make bookings, and complete payments (including MPESA STK Push integration). Administrators can manage flights, users, and monitor transactions through a secure dashboard.

Features
🔐 Authentication & Roles: Passenger and Admin access with JWT security

🛫 Flight Management: Create, update, and list available flights

📑 Bookings: Passengers can book seats with class and seat number tracking

💳 Payments: Integrated MPESA STK Push (sandbox) and Stripe support

📊 Admin Dashboard: Manage users, bookings, and payments with Spring Boot security

🗄️ Database: MySQL schema with ER diagrams and seeders for demo data


### Prerequisites
- Java 17+
- Maven 3.8+

Tech Stack
Backend: Java Spring Boot

Frontend: Thymeleaf templates, HTML/CSS/JS

Database: MySQL

Payments: MPESA Daraja API, Stripe

Security: Spring Security with JWT

Project Structure
Code
src/main/java/com/flyte/
 ├── controller/       # REST controllers
 ├── entity/           # JPA entities
 ├── repository/       # Spring Data repositories
 ├── service/          # Business logic
 └── security/         # JWT & role-based access
Getting Started
Clone the repo:

bash
git clone https://github.com/<your-org>/Flyte.git
cd Flyte
Configure your database in application.properties (see application.properties.example).

Run migrations and seeders (AdminSeeder, FlightSeeder).

Start the app:

bash
mvn spring-boot:run
Access the app at http://localhost:8080.

Contributors 👥
This project was built collaboratively by our team:

Lyean — Team Lead, Developer

Job — Developer

Beavon — Developer

Brandon — Developer

Together we designed, implemented, and tested Flyte as a professional group project.
