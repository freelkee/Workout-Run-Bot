package com.telegram.freelkee.workoutrunbot.model.training;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("workout")
public class Workout extends Training{
}
