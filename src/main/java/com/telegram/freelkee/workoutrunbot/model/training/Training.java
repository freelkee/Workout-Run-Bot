package com.telegram.freelkee.workoutrunbot.model.training;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;



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
    private  String trainingType;
}
