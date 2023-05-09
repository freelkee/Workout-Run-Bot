package com.telegram.freelkee.workoutrunbot.service;

import com.telegram.freelkee.workoutrunbot.model.Training;
import com.telegram.freelkee.workoutrunbot.model.User;
import com.telegram.freelkee.workoutrunbot.repository.TrainingRepository;
import com.telegram.freelkee.workoutrunbot.repository.UserRepository;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;


@RunWith(SpringRunner.class)
@SpringBootTest
class TrainingBotTest {
    @Autowired
    private TrainingBot trainingBot;
    @MockBean
    private UserRepository userRepository;
    private TrainingBot mockTrainingBot;
    private final Message message = new Message();
    private User user = new User();
    private final Chat chat = new Chat();
    @MockBean
    private TrainingRepository trainingRepository;


    @BeforeEach
    public void setup() {
        mockTrainingBot = Mockito.spy(trainingBot);
        Mockito.doNothing().when(mockTrainingBot).sendMessage(Mockito.anyLong(), Mockito.anyString());

        chat.setFirstName("John");
        chat.setUserName("john");
        chat.setId(1L);
        message.setChat(chat);

        user.setChatId(message.getChatId());
        user.setUserName(message.getChat().getUserName());
        user.setFirstName(message.getChat().getFirstName());
        user.setCondition(0);

    }

    @Test
    void registerUserWhenNotExist() {
        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.empty());
        Mockito.doNothing().when(mockTrainingBot).sendMessage(Mockito.anyLong(), Mockito.anyString());

        boolean isRegister = mockTrainingBot.registerUser(message);
        Assert.assertTrue(isRegister);

        InOrder inOrder = Mockito.inOrder(userRepository, mockTrainingBot);
        inOrder.verify(mockTrainingBot, Mockito.times(1)).sendMessage(chat.getId(),
                "Hi, " + message.getChat().getFirstName() + ", welcome to the training tracking app!");
        inOrder.verify(userRepository, Mockito.times(1)).findById(chat.getId());
        Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any(User.class));
        inOrder.verify(mockTrainingBot, Mockito.times(1)).sendMessage(chat.getId(),
                "We have created your profile in the database, " +
                        "now you can save your trainings and use all the functionality of the service. " +
                        "But we need some more of your data.");

    }

    @Test
    void registerUserWhenExist() {
        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        boolean isRegister = mockTrainingBot.registerUser(message);
        Assert.assertFalse(isRegister);

        InOrder inOrder = Mockito.inOrder(userRepository, mockTrainingBot);
        inOrder.verify(mockTrainingBot, Mockito.times(1)).sendMessage(chat.getId(),
                "Hi, " + message.getChat().getFirstName() + ", welcome to the training tracking app!");
        inOrder.verify(userRepository, Mockito.times(1)).findById(chat.getId());
        inOrder.verify(userRepository, Mockito.never()).save(user);
        inOrder.verify(mockTrainingBot, Mockito.times(1)).sendMessage(chat.getId(),
                "You are already registered in the system.");
    }

    @Test
    void replyToUsersMessagesSend() {
        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        message.setText("/send");
        mockTrainingBot.replyToUsersMessages(message);
        Mockito.verify(mockTrainingBot, Mockito.times(1)).sendMessage(chat.getId(),
                "You don't have permission for this command.");
    }

    @Test
    void replyToUsersMessagesNullUser() {
        user = null;
        Mockito.when(userRepository.findById(1L)).thenReturn(null);
        message.setText("");
        mockTrainingBot.replyToUsersMessages(message);
        Mockito.verify(mockTrainingBot, Mockito.times(1))
                .commandsForNullUser(message, chat.getId(), message.getText());
    }

    @Test
    void replyToUsersMessagesCondition0() {
        Mockito.when(userRepository.findByChatId(1L)).thenReturn(user);
        message.setText("");
        mockTrainingBot.replyToUsersMessages(message);
        Mockito.verify(mockTrainingBot, Mockito.times(1))
                .zeroConditionBlock(message, chat.getId(), user, message.getText());
    }
    @Test
    void replyToUsersMessagesCondition1() {
        user.setCondition(1);
        Mockito.when(userRepository.findByChatId(1L)).thenReturn(user);
        message.setText("");
        mockTrainingBot.replyToUsersMessages(message);
        Mockito.verify(mockTrainingBot, Mockito.times(1))
                .nonZeroConditionBlock(message, chat.getId(), user, message.getText());
    }

    @Test
    void buttonBlock() {
    }

    @Test
    @Disabled
    void sendMessage() throws TelegramApiException {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chat.getId());
        sendMessage.setText("123456");
        mockTrainingBot.sendMessage(chat.getId(), "123456");
        Mockito.verify(mockTrainingBot, Mockito.times(1))
                .execute(sendMessage);
    }

    @Test
    void sendMessageToAllUsersTest() {
        // arrange
        User user1 = new User();
        user1.setChatId(2L);
        user1.setUserName("Steve");
        user1.setFirstName("steve");
        user1.setCondition(0);

        User user2 = new User();
        user2.setChatId(3L);
        user2.setUserName("Lara");
        user2.setFirstName("lara");
        user2.setCondition(0);

        Mockito.when(userRepository.findAll()).thenReturn(Arrays.asList(user, user1, user2));
        String messageText = "/send Hello, users!";
        // act
        mockTrainingBot.sendMessageToAllUsers(messageText);

        // assert
        Mockito.verify(mockTrainingBot, Mockito.times(3))
                .sendMessage(Mockito.anyLong(), Mockito.eq("Hello, users!"));
    }

    @Test
    public void newTrainingResponse() throws ParseException {
        user.setCondition(2);
        user.setTrainings(new ArrayList<>());

        Training training = new Training();
        training.setTrainingType("run");
        training.setDistance(10000);
        training.setDuration(60);
        training.setAverageHeartRate(160);
        training.setDate(TrainingBot.convertStringToTimestamp("09.05.23/07.12"));

        Message message = new Message();
        message.setText("run\n60\n09.05.23/07.12\n160\n10000");

        Mockito.when(userRepository.save(user)).thenReturn(user);
        Mockito.when(trainingRepository.save(training)).thenReturn(training);

        mockTrainingBot.newTrainingResponse(message, user);


        assertEquals(1, user.getTrainings().size()); // quick fix
        Mockito.verify(userRepository, Mockito.times(1)).save(user);
        Mockito.verify(trainingRepository, Mockito.times(1)).save(Mockito.any(Training.class));
        Mockito.verify(mockTrainingBot, Mockito.times(1))
                .sendMessage(chat.getId(), "Your training was recorded.");
        assertEquals(user.getCondition(),0);
    }

    @Test
    void updateTrainingResponse() {
    }

    @Test
    void setTrainingByText() {
    }

    @Test
    void calculateSpeed() {
    }

    @Test
    void convertStringToTimestamp() {
    }

    @Test
    void convertTimestampToString() {
    }

    @Test
    void deleteData() {
    }

    @Test
    void myDataCommandReceived() {
    }

    @Test
    void startCommandReceived() {
    }
}
