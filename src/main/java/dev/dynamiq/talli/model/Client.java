package dev.dynamiq.talli.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// A model class — like `class Client extends Model` in Laravel.
// The difference: no ORM magic. You define every field explicitly.
// Java doesn't have $fillable/$guarded — you control access via constructors and setters.
public class Client {

    // In Java, database IDs are typically int or long (not auto-incrementing by the model —
    // that's the DB's job, just like Laravel's $incrementing).
    private int id;
    private String name;
    private String email;
    // BigDecimal = the correct type for money. NEVER use float/double for currency —
    // same rule as PHP/Python. BigDecimal is like PHP's bcmath functions but as a type.
    private BigDecimal rate;
    // enum would be cleaner, but String keeps it simple for now.
    // "hourly", "project", "retainer"
    private String rateType;
    private String notes;
    private LocalDate createdAt;

    // No-arg constructor — needed by the DB layer to create empty objects and fill them.
    // Like Laravel's `new Client()` before calling `->fill()`.
    public Client() {
        this.createdAt = LocalDate.now();
    }

    // Full constructor — like `Client::create([...])` in Laravel.
    public Client(String name, String email, BigDecimal rate, String rateType, String notes) {
        this.name = name;
        this.email = email;
        this.rate = rate;
        this.rateType = rateType;
        this.notes = notes;
        this.createdAt = LocalDate.now();
    }

    // Getters and setters — Java's equivalent of $model->attribute.
    // In Laravel, Eloquent gives you __get/__set magic. Java makes you be explicit.
    // Annoying but the IDE auto-generates these (Alt+Insert in IntelliJ, or right-click > Source in VS Code).
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }

    public String getRateType() { return rateType; }
    public void setRateType(String rateType) { this.rateType = rateType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return name + " (" + rateType + " — $" + rate + ")";
    }
}
