package com.talli.service;

import com.talli.db.Database;
import com.talli.model.Client;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// The service/repository layer — like a Laravel Service or Repository class.
// All database operations for clients live here. The UI never touches SQL directly,
// just like your controllers shouldn't have raw DB::select() calls.
public class ClientService {

    // Returns all clients. Like Client::all() in Eloquent.
    public List<Client> getAll() {
        List<Client> clients = new ArrayList<>();
        String sql = "SELECT * FROM clients ORDER BY name";

        // PreparedStatement = parameterized queries. Same as PDO prepared statements.
        // ALWAYS use these, never concatenate user input into SQL. Same OWASP rules as PHP.
        try (PreparedStatement ps = Database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            // rs.next() advances the cursor row by row — like foreach on a PDO result.
            while (rs.next()) {
                clients.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return clients;
    }

    // Find by ID. Like Client::find($id).
    public Client getById(int id) {
        String sql = "SELECT * FROM clients WHERE id = ?";
        try (PreparedStatement ps = Database.getConnection().prepareStatement(sql)) {
            // Parameters are 1-indexed (not 0). Yeah, it's annoying.
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Like returning null in PHP — caller must handle it.
    }

    // Insert a new client. Like Client::create([...]).
    // Returns the generated ID.
    public int create(Client client) {
        String sql = "INSERT INTO clients (name, email, rate, rate_type, notes, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = Database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, client.getName());
            ps.setString(2, client.getEmail());
            ps.setBigDecimal(3, client.getRate());
            ps.setString(4, client.getRateType());
            ps.setString(5, client.getNotes());
            ps.setString(6, client.getCreatedAt().toString());
            ps.executeUpdate();

            // Get the auto-incremented ID back — like $client->id after save() in Laravel.
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    client.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // Update an existing client. Like $client->update([...]).
    public boolean update(Client client) {
        String sql = "UPDATE clients SET name = ?, email = ?, rate = ?, rate_type = ?, notes = ? WHERE id = ?";
        try (PreparedStatement ps = Database.getConnection().prepareStatement(sql)) {
            ps.setString(1, client.getName());
            ps.setString(2, client.getEmail());
            ps.setBigDecimal(3, client.getRate());
            ps.setString(4, client.getRateType());
            ps.setString(5, client.getNotes());
            ps.setInt(6, client.getId());
            return ps.executeUpdate() > 0; // Returns true if a row was affected.
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Delete. Like $client->delete().
    public boolean delete(int id) {
        String sql = "DELETE FROM clients WHERE id = ?";
        try (PreparedStatement ps = Database.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Maps a database row to a Client object.
    // This is what Eloquent does automatically with its hydrate() method.
    // In Java, you do it by hand (or use an ORM like Hibernate to skip this).
    private Client mapRow(ResultSet rs) throws SQLException {
        Client client = new Client();
        client.setId(rs.getInt("id"));
        client.setName(rs.getString("name"));
        client.setEmail(rs.getString("email"));
        client.setRate(rs.getBigDecimal("rate"));
        client.setRateType(rs.getString("rate_type"));
        client.setNotes(rs.getString("notes"));
        String dateStr = rs.getString("created_at");
        if (dateStr != null) {
            client.setCreatedAt(LocalDate.parse(dateStr));
        }
        return client;
    }
}
