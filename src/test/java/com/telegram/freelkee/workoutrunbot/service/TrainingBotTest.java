package com.telegram.freelkee.workoutrunbot.service;

import com.telegram.freelkee.workoutrunbot.model.User;
import com.telegram.freelkee.workoutrunbot.repository.UserRepository;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.sql.Timestamp;
import java.util.Optional;


@RunWith(SpringRunner.class)
@SpringBootTest
class TrainingBotTest {
    @Autowired
    private TrainingBot trainingBot;
    @MockBean
    private UserRepository userRepository;


    @BeforeEach
    public void setup() {

    }

    @Test
    void registerUserWhenNotExist() {
        TrainingBot mockTrainingBot = Mockito.spy(trainingBot);
        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.empty());
        Mockito.doNothing().when(mockTrainingBot).sendMessage(Mockito.anyLong(), Mockito.anyString());

        Message message = new Message();
        Chat chat = new Chat();
        chat.setFirstName("John");
        chat.setUserName("john");
        chat.setId(1L);
        message.setChat(chat);

        User user = new User();
        user.setChatId(message.getChatId());
        user.setUserName(message.getChat().getUserName());
        user.setFirstName(message.getChat().getFirstName());
        user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
        user.setCondition(0);

        boolean isRegister = mockTrainingBot.registerUser(message);
        Assert.assertTrue(isRegister);

        Mockito.verify(userRepository, Mockito.times(1)).findById(chat.getId());
        Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any(User.class));
        Mockito.verify(mockTrainingBot, Mockito.times(1)).sendMessage(chat.getId(),
                "Hi, " + message.getChat().getFirstName() + ", welcome to the training tracking app!");
        Mockito.verify(mockTrainingBot, Mockito.times(1)).sendMessage(chat.getId(),
                "We have created your profile in the database, " +
                        "now you can save your trainings and use all the functionality of the service. " +
                        "But we need some more of your data.");

    }
    @Test
    void registerUserWhenExist() {
        Message message = new Message();
        Chat chat = new Chat();
        chat.setFirstName("John");
        chat.setUserName("john");
        chat.setId(1L);
        message.setChat(chat);

        User user = new User();
        user.setChatId(message.getChatId());
        user.setUserName(message.getChat().getUserName());
        user.setFirstName(message.getChat().getFirstName());
        user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
        user.setCondition(0);

        TrainingBot mockTrainingBot = Mockito.spy(trainingBot);
        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Mockito.doNothing().when(mockTrainingBot).sendMessage(Mockito.anyLong(), Mockito.anyString());

        boolean isRegister = mockTrainingBot.registerUser(message);
        Assert.assertFalse(isRegister);

        Mockito.verify(userRepository, Mockito.times(1)).findById(chat.getId());
        Mockito.verify(userRepository, Mockito.times(0)).save(Mockito.any(User.class));
        Mockito.verify(mockTrainingBot, Mockito.times(1)).sendMessage(chat.getId(),
                "Hi, " + message.getChat().getFirstName() + ", welcome to the training tracking app!");
        Mockito.verify(mockTrainingBot, Mockito.times(1)).sendMessage(chat.getId(),
                "You are already registered in the system.");
    }
}
