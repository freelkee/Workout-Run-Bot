package com.telegram.freelkee.workoutrunbot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;


//@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
//@DiscriminatorColumn(name="training_type",   discriminatorType = DiscriminatorType.STRING)
//@DiscriminatorValue("null")
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "training")
public class Training {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Timestamp date;
    private Integer duration;
    private Integer calories;
    private Integer averageHeartRate;
    private Integer distance;
    private Double speed;
    private String trainingType;

    @Override
    public String toString() {
        return "Training ID: " + id + "\n" +
                "Training Type: " + trainingType + "\n" +
                "Date: " + new SimpleDateFormat("dd.MM.yyyy/HH.mm").format(date) + "\n" +
                "Average Heart Rate: " + averageHeartRate + " bpm\n" +
                "Distance: " + distance + " meters\n" +
                "Duration: " + duration + "min\n" +
                "Calories: " + calories + "ccal\n" +
                "Speed: " + speed + " km/h" + "\n";
    }
}
