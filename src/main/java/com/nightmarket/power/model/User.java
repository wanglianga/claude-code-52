package com.nightmarket.power.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class User {
    private String username;
    private String password;          // BCrypt
    private String name;
    private Role role;
    private String phone;
    private LocalDateTime createdAt;

    public User(String username, String password, String name, Role role, String phone) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.role = role;
        this.phone = phone;
        this.createdAt = LocalDateTime.now();
    }
}
