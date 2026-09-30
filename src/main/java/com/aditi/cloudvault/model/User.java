package com.aditi.cloudvault.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id //primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY ) //dont make me manually assign the id, let the database generate it. khud se user.setid(47) ye sb ni krna pdega, khud hi postgresql generate krdega id
    private Long id;

    @Column (nullable = false, unique = true)
    private String email;


    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToOne
    private User owner;

    public User() {
    }
    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


}
