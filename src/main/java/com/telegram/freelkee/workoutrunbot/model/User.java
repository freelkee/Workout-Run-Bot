package com.telegram.freelkee.workoutrunbot.model;

import jakarta.persistence.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity(name = "usersDataTable")
public class User {
    @Id
    private Long chatId;
    private String firstName;
    private String lastName;
    private String userName;
    private Timestamp registeredAt;
    private int condition;

    @Min(value = 1, message = "Weight must be a positive value")
    @Max(value = 700, message = "Too much weight")
    private Integer weight;

    @Min(value = 1, message = "Height must be a positive value")
    @Max(value = 300, message = "Too much height")
    private Integer height;

    @Min(value = 1, message = "Age must be a positive value")
    @Max(value = 300, message = "Too old")
    private Integer age;
    private Long updateTrainingId;
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    public List<Training> trainings;

    @PrePersist
    protected void onCreate() {
        registeredAt = new Timestamp(System.currentTimeMillis());
    }

    @Override
    public String toString() {
        return "User ID: " + chatId + "\n" +
                "First Name: " + firstName + "\n" +
                "Last Name: " + lastName + "\n" +
                "Username: " + userName + "\n" +
                "Registered At: " + registeredAt + "\n" +
                "Condition: " + condition + "\n" +
                "Weight: " + weight + "\n" +
                "Height: " + height + "\n" +
                "Age: " + age;
    }
}
