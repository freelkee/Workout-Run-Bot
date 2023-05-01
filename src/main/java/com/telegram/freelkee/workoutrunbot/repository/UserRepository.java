package com.telegram.freelkee.workoutrunbot.repository;

import com.telegram.freelkee.workoutrunbot.model.User;

import org.springframework.data.repository.CrudRepository;


public interface UserRepository extends CrudRepository<User, Long> {
    User findByChatId(Long chatId);
}
