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

import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@RunWith(SpringRunner.class)
@SpringBootTest
class TrainingBotTest {
    @Autowired
    private TrainingBot trainingBot;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private TrainingRepository trainingRepository;
    private TrainingBot mockTrainingBot;
    private final Message message = new Message();
    private final Chat chat = new Chat();
    private User user = new User();
    private final Training training = new Training();
    @BeforeEach
    public void setup() throws ParseException {
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

        training.setTrainingType("run");
        training.setDistance(10000);
        training.setDuration(60);
        training.setAverageHeartRate(160);
        training.setDate(TrainingBot.convertStringToTimestamp("09.05.23/07.12"));
    }

    @Test
    void registerUserWhenNotExist() {
        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.empty());

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
    @Disabled
    void sendMessage() throws TelegramApiException {
        Mockito.reset(mockTrainingBot);
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chat.getId());
        sendMessage.setText("123456");
        mockTrainingBot.sendMessage(chat.getId(), "123456");
        Mockito.verify(mockTrainingBot, Mockito.times(1))
                .execute(sendMessage);
    }

    @Test
    void sendMessageToAllUsersTest() {

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

        mockTrainingBot.sendMessageToAllUsers(messageText);

        Mockito.verify(mockTrainingBot, Mockito.times(3))
                .sendMessage(Mockito.anyLong(), Mockito.eq("Hello, users!"));
    }

    @Test
    public void newTrainingResponse() {
        user.setCondition(2);
        user.setTrainings(new ArrayList<>());

        message.setText("run\n60\n09.05.23/07.12\n160\n10000");

        Mockito.when(userRepository.save(user)).thenReturn(user);
        Mockito.when(trainingRepository.save(training)).thenReturn(training);

        mockTrainingBot.newTrainingResponse(message, user);
        try {
            training.setSpeed(TrainingBot.calculateSpeed(training.getDistance(), training.getDuration()));
        } catch (Exception e) {
            training.setSpeed(null);
        }

        assertTrue(user.getTrainings().contains(training));
        Mockito.verify(userRepository, Mockito.times(1)).save(user);
        Mockito.verify(trainingRepository, Mockito.times(1)).save(Mockito.any(Training.class));
        Mockito.verify(mockTrainingBot, Mockito.times(1))
                .sendMessage(chat.getId(), "Your training was recorded.");
        assertEquals(user.getCondition(),0);
    }

    @Test
    void updateTrainingResponse() {
        training.setId(1L);

        message.setText("run\n50\n09.05.23/07.12\n160\n10000");

        user.setTrainings(new ArrayList<>());
        user.addTtraining(training);
        user.setUpdateTrainingId(1L);
        user.setCondition(5);

        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.ofNullable(user));
        Mockito.when(trainingRepository.findById(1L)).thenReturn(Optional.of(training));

        mockTrainingBot.updateTrainingResponse(message, user);

        InOrder inOrder = Mockito.inOrder(userRepository, trainingRepository,mockTrainingBot);
        inOrder.verify(trainingRepository, Mockito.times(1)).findById(training.getId());
        inOrder.verify(userRepository, Mockito.times(2)).save(user);
        inOrder.verify(trainingRepository, Mockito.times(1)).save(training);
        inOrder.verify(mockTrainingBot, Mockito.times(1))
                .sendMessage(chat.getId(), "Your training was updated.");
    }

    @Test
    void calculateSpeed() {
        double distanceMeters = 1000.0;
        double timeMinutes = 60.0;
        double expectedSpeed = 1.0; // km/hour

        double actualSpeed = TrainingBot.calculateSpeed(distanceMeters, timeMinutes);
        assertEquals(expectedSpeed, actualSpeed, 0.001);
    }

    @Test
    void convertStringToTimestamp() throws ParseException {
        String dateString = "10.05.23/13.30";
        long expectedTime = 1683714600000L;
        Timestamp actualTimestamp = TrainingBot.convertStringToTimestamp(dateString);

        assertEquals(expectedTime, actualTimestamp.getTime());
    }

    @Test
    void convertTimestampToString() throws ParseException {
        String dateString = "01.01.22/12.30";
        Timestamp timestamp = TrainingBot.convertStringToTimestamp(dateString);
        String expected = "01.01.22/12.30";
        String result = TrainingBot.convertTimestampToString(timestamp);
        assertEquals(expected, result);
    }

    @Test
    void deleteDataCommandReceived() {
        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.ofNullable(user));

        boolean isDelete = mockTrainingBot.deleteDataCommandReceived(user.getChatId());
        Assert.assertTrue(isDelete);

        InOrder inOrder = Mockito.inOrder(userRepository, mockTrainingBot);
        inOrder.verify(userRepository, Mockito.times(1)).findById(chat.getId());
        inOrder.verify(userRepository, Mockito.times(1)).delete(user);
    }

    @Test
    void myDataCommandReceived() {
        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.ofNullable(user));

        mockTrainingBot.myDataCommandReceived(message);

        InOrder inOrder = Mockito.inOrder(userRepository, mockTrainingBot);
        inOrder.verify(userRepository, Mockito.times(1)).findById(chat.getId());
        inOrder.verify(mockTrainingBot, Mockito.times(1))
                .sendMessage(message.getChatId(), user.toString());
    }
}
