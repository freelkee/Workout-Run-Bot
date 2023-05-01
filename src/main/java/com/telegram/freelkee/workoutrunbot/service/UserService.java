package com.telegram.freelkee.workoutrunbot.service;

import com.telegram.freelkee.workoutrunbot.model.User;
import com.telegram.freelkee.workoutrunbot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User registerUser(Long chatId, String firstName, String lastName, String userName) {
        // Проверка наличия пользователя с таким же chatId
        if (userRepository.findByChatId(chatId) != null) {
            throw new RuntimeException("Пользователь с таким chatId уже существует");
        }

        // Создание нового пользователя
        User user = new User();
        user.setChatId(chatId);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUserName(userName);
        user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

        // Сохранение пользователя в базе данных
        return userRepository.save(user);
    }

    public User getUserByChatId(Long chatId) {
        User user = userRepository.findByChatId(chatId);
        if (user == null) {
            throw new RuntimeException("Пользователь не найден");
        }
        return user;
    }
}
