public class Booking {
    // Other fields...
    private String ticketNumber;

    public String getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    // Method for ticket number generation
    public void generateTicketNumber() {
        // Your logic for ticket number generation
        // Ensure there are no extra blank lines
        this.ticketNumber = "SomeGeneratedNumber";
    }
}