package com.telegram.freelkee.workoutrunbot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "training")
public class Training {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Date is required")
    private Timestamp date;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be a positive value")
    private Integer duration;
    private Integer calories;

    @Min(value = 1, message = "Average Heart Rate must be a positive value")
    @Max(value = 240, message = "Average Heart Rate is too high")
    private Integer averageHeartRate;

    @Min(value = 1, message = "Distance must be a positive value")
    private Integer distance;
    private Double speed;
    //    @NotNull(message = "Training Type is required")
//    @Pattern(regexp = "^(run|workout)$", message = "Training Type must be either 'run' or 'workout'")
    private String trainingType;

    @Override
    public String toString() {
        return "Training ID: " + id + "\n" +
                "Training Type: " + trainingType + "\n" +
                "Date: " + new SimpleDateFormat("dd.MM.yyyy/HH.mm").format(date) + "\n" +
                "Average Heart Rate: " + averageHeartRate + " bpm\n" +
                "Distance: " + distance + " meters\n" +
                "Duration: " + duration + " min\n" +
                "Calories: " + calories + " ccal\n" +
                "Speed: " + speed + " km/h" + "\n";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Training training = (Training) obj;
        return Objects.equals(id, training.id) &&
                Objects.equals(date, training.date) &&
                Objects.equals(duration, training.duration) &&
                Objects.equals(calories, training.calories) &&
                Objects.equals(averageHeartRate, training.averageHeartRate) &&
                Objects.equals(distance, training.distance) &&
                Objects.equals(speed, training.speed) &&
                Objects.equals(trainingType, training.trainingType);
    }
    @Override
    public int hashCode() {
        return Objects.hash(id, date, duration, calories, averageHeartRate, distance, speed, trainingType);
    }
}
