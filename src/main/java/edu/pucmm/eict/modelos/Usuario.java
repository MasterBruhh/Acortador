package edu.pucmm.eict.modelos;

import org.bson.types.ObjectId;

public class Usuario {
    private ObjectId id;            // ObjectId para MongoDB
    private String username;
    private String password;
    private String role;

    public Usuario() {
        // Constructor vacío (necesario si deseas mapeo flexible)
    }

    // Constructor para usuario normal
    public Usuario(String username, String password) {
        this.username = username;
        this.password = password;
        this.role = "user";
    }

    // Constructor para usuario con rol específico
    public Usuario(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    // Getters y setters
    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }
    public void setRole(String role) {
        this.role = role;
    }
}
