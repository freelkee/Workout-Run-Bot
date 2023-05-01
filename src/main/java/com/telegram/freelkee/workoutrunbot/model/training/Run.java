package com.telegram.freelkee.workoutrunbot.model.training;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("run")
public class Run extends Training {
    private int distance;
    private int speed;
}
