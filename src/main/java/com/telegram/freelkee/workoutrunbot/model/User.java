package com.telegram.freelkee.workoutrunbot.model;

import jakarta.persistence.*;

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
    private Integer weight;
    private Integer height;
    private Integer age;
    private Long updateTrainingId;
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    public List<Training> trainings;


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
