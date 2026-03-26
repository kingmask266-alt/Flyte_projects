# ✈ Flyte — Flight Booking System

A full Spring Boot flight booking system with SQLite, Thymeleaf, and JPA.

---

## 🚀 How to Run

### Prerequisites
- Java 17+
- Maven 3.8+

### Steps
```bash
# 1. Navigate into the project
cd Flyte

# 2. Build the project
mvn clean install

# 3. Run the application
mvn spring-boot:run
```

### Access the App
| URL | Description |
|-----|-------------|
| http://localhost:8080/ | Redirects to Admin Dashboard |
| http://localhost:8080/admin | Admin Dashboard |
| http://localhost:8080/flights | All Flights |
| http://localhost:8080/flights/add | Add New Flight |
| http://localhost:8080/bookings | All Bookings |
| http://localhost:8080/book/{flightId} | Book a Flight |

---

## 🏗 Project Structure

```
Flyte/
├── pom.xml
└── src/main/
    ├── java/com/flyte/
    │   ├── FlyteApplication.java        ← Entry point
    │   ├── DataSeeder.java              ← Sample seed data
    │   ├── entity/
    │   │   ├── Flight.java
    │   │   └── Booking.java
    │   ├── repository/
    │   │   ├── FlightRepository.java
    │   │   └── BookingRepository.java
    │   ├── service/
    │   │   ├── FlightService.java
    │   │   └── BookingService.java
    │   └── controller/
    │       ├── AdminController.java
    │       ├── FlightController.java
    │       └── BookingController.java
    └── resources/
        ├── application.properties
        └── templates/
            ├── admin.html
            ├── flyte_demo.html
            ├── flights/
            │   ├── list.html
            │   └── add.html
            └── bookings/
                ├── list.html
                └── book.html
```

---

## 🗄 Database
- SQLite file: `flyte_booking.db` (auto-created on first run)
- Seed data: 4 flights + 6 bookings loaded automatically

## 📦 Tech Stack
- **Spring Boot 3.2**
- **Spring Data JPA** (Hibernate)
- **SQLite** (via Xerial JDBC)
- **Thymeleaf** (HTML templates)
- **Maven**
