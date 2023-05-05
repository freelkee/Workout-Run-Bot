package com.telegram.freelkee.workoutrunbot.service;

import com.telegram.freelkee.workoutrunbot.config.BotConfig;
import com.telegram.freelkee.workoutrunbot.model.Training;
import com.telegram.freelkee.workoutrunbot.model.User;
import com.telegram.freelkee.workoutrunbot.repository.TrainingRepository;
import com.telegram.freelkee.workoutrunbot.repository.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
@Slf4j
public class WorkoutBot extends TelegramLongPollingBot {

    private final UserRepository userRepository;
    private final TrainingRepository trainingRepository;

    final BotConfig config;

    static final String HELP_TEXT = """
            This bot is created to help you track your workouts and running activities.

            You can execute commands from the main menu on the left or by typing a command:

            Type /start to see a welcome message.

            Type /newtraining to add a new training to your diary.
                        
            Type /mytrainings to see your training history.

            Type /statistics to view your training statistics.

            Type /help to see this message again.

            Type /settings to customize your preferences.
                        
            If you want to exit without saving from any menu, write "exit".
            """;

    static final String NEWTRAINING_TEXT = """
            Please write down the data about your training according to this template.
                        
            Type (workout, run)
            Duration (in minutes)
            Date (dd.MM.YY/HH.mm)      \s
            Average Heart Rate (BPM, send "null" if you don't have)
            Distance (in meters, send "null" if you don't have)
                        
            If you want to exit without saving, write "exit".
            """;

    static final String YES_BUTTON = "YES_BUTTON";
    static final String NO_BUTTON = "NO_BUTTON";
    private static final String DELETE_BUTTON = "DELETE_BUTTON";
    static final String FINALLY_DELETE_BUTTON = "FINALLY_DELETE_BUTTON";
    static final String WEIGHT_BUTTON = "WEIGHT_BUTTON";
    static final String HEIGHT_BUTTON = "HEIGHT_BUTTON";
    private static final String AGE_BUTTON = "AGE_BUTTON";
    private static final String MYDATA_BUTTON = "MYDATA_BUTTON";
    private static final String API_BUTTON = "API_BUTTON";
    private static final String UPDATE_TR_BUTTON = "UPDATE_TR_BUTTON";
    private static final String DELETE_TR_BUTTON = "DELETE_TR_BUTTON";

    Integer[] newTrainingArray = {0, 1, 2, 3, 4};
    Integer[] updateTrainingArray = {5, 6, 7, 8, 9};
    Integer[] settingArray = {10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
    Integer[] myTrainingsArray = {20, 21, 22, 23, 24};
    Integer[] statisticsArray = {25, 26, 27, 28, 29};

    static final String ERROR_TEXT = "Error occurred: ";
    static final String TRY_AGAIN_TEXT = ", try again or sent \"exit\".";

    public WorkoutBot(BotConfig config, UserRepository userRepository, TrainingRepository trainingRepository) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/newtraining", "record a new training"));
        listOfCommands.add(new BotCommand("/mytrainings", "get your data training"));
        listOfCommands.add(new BotCommand("/statistics", "get your stats"));
        listOfCommands.add(new BotCommand("/help", "info how to use this bot"));
        listOfCommands.add(new BotCommand("/settings", "set your preferences"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bots command list: " + e.getMessage());
        }
        this.userRepository = userRepository;
        this.trainingRepository = trainingRepository;
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    //@Transactional
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        if (update.hasMessage() && message.hasText()) {
            replyToUsersMessages(message);
        } else if (update.hasCallbackQuery()) {
            buttonBlock(update);
        }
    }

    private void replyToUsersMessages(Message message) {
        long chatId = message.getChatId();
        User user = userRepository.findByChatId(chatId);
        String messageText = message.getText();

        if (messageText.contains("/send") && config.getOwnerId() == chatId) {
            sendMessageToAllUsers(messageText);
        } else if (messageText.contains("/send") && config.getOwnerId() != chatId) {
            sendMessage(chatId, "You don't have permission for this command.");
        } else if (user == null) {
            commandsForNullUser(message, chatId, messageText);
        } else if (user.getCondition() == 0) {
            zeroConditionBlock(message, chatId, user, messageText);
        } else {
            nonZeroConditionBlock(message, chatId, user, messageText);
        }
    }

    private void buttonBlock(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        long messageId = callbackQuery.getMessage().getMessageId();
        long chatId = callbackQuery.getMessage().getChatId();

        User user = userRepository.findByChatId(chatId);

        String callbackData = callbackQuery.getData();
        EditMessageText editMessageText = new EditMessageText();

        if (callbackData.equals(YES_BUTTON)) {
            String text = "You pressed YES button";
            executeEditMessageText((int) messageId, chatId, editMessageText, text);

        } else if (callbackData.equals(NO_BUTTON)) {
            String text = "You pressed NO button";
            executeEditMessageText((int) messageId, chatId, editMessageText, text);

        } else if (callbackData.equals(FINALLY_DELETE_BUTTON)) {
            if (deleteDataCommandReceived(chatId)) {
                String text = "You deleted your data";
                executeEditMessageText((int) messageId, chatId, editMessageText, text);
            }
        } else if (callbackData.equals(WEIGHT_BUTTON)) {
            user.setCondition(10);
            sendMessage(chatId, "Enter your weight in kilograms");
            userRepository.save(user);

        } else if (callbackData.equals(HEIGHT_BUTTON)) {
            user.setCondition(11);
            sendMessage(chatId, "Enter your height in centimeters");
            userRepository.save(user);

        } else if (callbackData.equals(AGE_BUTTON)) {
            user.setCondition(12);
            sendMessage(chatId, "Enter your age in years");
            userRepository.save(user);

        } else if (callbackData.equals(MYDATA_BUTTON)) {
            user.setCondition(0);
            sendMessage(chatId, "This is all the data we have about you:\n" + user);
            userRepository.save(user);

        } else if (callbackData.equals("DEL")) {
            user.setCondition(14);
            deleteData(chatId);
            userRepository.save(user);

        } else if (callbackData.equals(API_BUTTON)) {
            user.setCondition(0);
            sendMessage(chatId, "This option is in development.");
            userRepository.save(user);

        } else if (callbackData.startsWith(UPDATE_TR_BUTTON)) {
            Long id = Long.parseLong(callbackData.split(" ")[1]);
            Training training = trainingRepository.findById(id).orElse(null);
            if (training != null) {
                user.setCondition(5);
                String text = "This is your training data, copy it and send the corrected version.\n\n" +
                        training.getTrainingType() + "\n" +
                        training.getDuration() + "\n" +
                        convertTimestampToString(training.getDate()) + "\n" +
                        training.getAverageHeartRate() + "\n" +
                        training.getDistance() + "\n";
                executeEditMessageText((int) messageId, chatId, editMessageText, text);
                user.setUpdateTrainingId(id);
                userRepository.save(user);
            }

        } else if (callbackData.startsWith(DELETE_TR_BUTTON)) {
            Long id = Long.parseLong(callbackData.split(" ")[1]);
            trainingRepository.deleteById(id);
            executeEditMessageText((int) messageId, chatId, editMessageText, "Your training was deleted.");
        }
    }

    private void commandsForNullUser(Message message, long chatId, String messageText) {
        if (messageText.trim().equalsIgnoreCase("/start")) {
            registerUser(message);
            startCommandReceived(chatId);
        } else {
            sendMessage(chatId, "You are not registered at the moment, write /start to get started.");
        }
    }

    private void sendMessageToAllUsers(String messageText) {
        var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
        var users = userRepository.findAll();
        for (User user1 : users) {
            sendMessage(user1.getChatId(), textToSend);
        }
    }

    private void zeroConditionBlock(Message message, long chatId, User user, String messageText) {
        switch (messageText.trim().toLowerCase()) {
            case "/start" -> {
                registerUser(message);
                startCommandReceived(chatId);
            }
            case "/help" -> sendMessage(chatId, HELP_TEXT);
            case "/mydata" -> myDataCommandReceived(message);
            case "/deletedata" -> deleteData(chatId);
            case "/newtraining" -> {
                user.setCondition(1);
                userRepository.save(user);
                newTrainingResponse(message, user);
            }
            case "/mytrainings" -> {
                user.setCondition(20);
                userRepository.save(user);
                myTrainingsCommandResponse(message, user);
            }
            case "/settings" -> settingsCommandResponse(user);

            case "/statistics" -> {
                user.setCondition(25);
                userRepository.save(user);
                statisticsResponse(message, user);
            }
            default -> sendMessage(chatId, "Sorry,command was not recognized");
        }
    }

    private void nonZeroConditionBlock(Message message, long chatId, User user, String messageText) {
        if (messageText.trim().equalsIgnoreCase("exit")) {
            exit(chatId, user);

        } else if (Arrays.asList(newTrainingArray).contains(user.getCondition())) {
            newTrainingResponse(message, user);

        } else if (Arrays.asList(updateTrainingArray).contains(user.getCondition())) {
            updateTrainingResponse(message, user);

        } else if (Arrays.asList(settingArray).contains(user.getCondition())) {
            setting(message, user);

        } else if (Arrays.asList(myTrainingsArray).contains(user.getCondition())) {
            myTrainingsCommandResponse(message, user);

        } else if (Arrays.asList(statisticsArray).contains(user.getCondition())) {
            statisticsResponse(message, user);
        }
    }

    private void exit(long chatId, User user) {
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), "Your condition has changed.");
        ReplyKeyboardRemove replyKeyboardRemove = new ReplyKeyboardRemove();
        replyKeyboardRemove.setRemoveKeyboard(true);
        sendMessage.setReplyMarkup(replyKeyboardRemove);
        user.setCondition(0);
        userRepository.save(user);
        executor(sendMessage);
    }

    private void newTrainingResponse(Message message, User user) {
        Long chatId = user.getChatId();
        String text = message.getText().trim().toLowerCase();
        switch (user.getCondition()) {
            case 0 -> sendMessage(chatId, "Exit from creating а new Trainings.");
            case 1 -> {
                sendMessage(chatId, NEWTRAINING_TEXT);
                user.setCondition(2);
                userRepository.save(user);
            }
            case 2 -> {
                Training training = new Training();
                try {
                    setTrainingByText(user, text, training);
                } catch (Exception e) {
                    sendMessage(chatId, ERROR_TEXT + e.getMessage() + TRY_AGAIN_TEXT);
                    log.error(ERROR_TEXT + e.getMessage());
                    return;
                }
                user.trainings.add(training);
                user.setCondition(0);
                trainingRepository.save(training);
                userRepository.save(user);
                log.info("User " + user.getFirstName() + "  recorded training " + training.getId());
                sendMessage(chatId, "Your training was recorded.");

            }
        }
    }

    private void updateTrainingResponse(Message message, User user) {
        String text = message.getText().trim().toLowerCase();
        Long trainingId = user.getUpdateTrainingId();
        if (user.getCondition() == 5) {
            Training training = trainingRepository.findById(trainingId).orElse(null);
            userRepository.save(user);
            if (training == null) {
                sendMessage(user.getChatId(), "Couldn't find a training.");
                log.error("Couldn't find a training.");
            } else {
                try {
                    setTrainingByText(user, text, training);
                } catch (Exception e) {
                    sendMessage(user.getChatId(), ERROR_TEXT + e.getMessage() + TRY_AGAIN_TEXT);
                    log.error(ERROR_TEXT + e.getMessage());
                    return;
                }

                user.setCondition(0);
                user.setUpdateTrainingId(null);
                userRepository.save(user);

                trainingRepository.save(training); // Сохранение после обновления объекта Training
                log.info("User " + user.getFirstName() + " updated training " + training.getId());
                sendMessage(message.getChatId(), "Your training was updated.");
            }
        }
    }

    private void setting(Message message, User user) {
        Long chatId = user.getChatId();
        String text = message.getText().trim().toLowerCase();
        switch (user.getCondition()) {
            case 0 -> sendMessage(chatId, "Exit from setting.");
            case 10 -> {
                user.setWeight(Integer.parseInt(text));
                updateData(user);
            }
            case 11 -> {
                user.setHeight(Integer.parseInt(text));
                updateData(user);
            }
            case 12 -> {
                user.setAge(Integer.parseInt(text));
                updateData(user);
            }
        }
    }

    private void myTrainingsCommandResponse(Message message, User user) {
        Long chatId = user.getChatId();
        String text = message.getText().trim().toLowerCase();
        switch (user.getCondition()) {
            case 0 -> sendMessage(chatId, "Exit from trainings.");
            case 20 -> {
                sendMessage(chatId, "How many trainings do you want to see?");
                user.setCondition(21);
                userRepository.save(user);
            }
            case 21 -> {

                List<Training> trainings;
                try {
                    int limit = Integer.parseInt(text);
                    trainings = user.getTrainings()
                            .stream()
                            .sorted(Comparator.comparing(Training::getDate).reversed())
                            .limit(limit)
                            .toList();
                } catch (Exception e) {
                    log.error(ERROR_TEXT + e.getMessage());
                    sendMessage(chatId, ERROR_TEXT + e.getMessage() + TRY_AGAIN_TEXT);
                    return;
                }

                if (trainings.size() == 0) {
                    sendMessage(chatId, "You don't have a record, try to record a new one using the /newtraining command");
                    user.setCondition(0);
                    userRepository.save(user);
                    return;
                }
                sendMessage(chatId, "Here are your " + trainings.size() + " trainings sorted by date");

                InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);

                for (Training training : trainings) {
                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    List<InlineKeyboardButton> level1 = new ArrayList<>();
                    var button1 = new InlineKeyboardButton();
                    var button2 = new InlineKeyboardButton();

                    button1.setText("Update");
                    button1.setCallbackData(UPDATE_TR_BUTTON + " " + training.getId());
                    level1.add(button1);

                    button2.setText("Delete");
                    button2.setCallbackData(DELETE_TR_BUTTON + " " + training.getId());
                    level1.add(button2);

                    rows.add(level1);
                    inlineKeyboardMarkup.setKeyboard(rows);

                    sendMessage.setText(training.toString());
                    sendMessage.setReplyMarkup(inlineKeyboardMarkup);
                    executor(sendMessage);
                }
                user.setCondition(0);
                userRepository.save(user);
                log.info("the user " + user.getFirstName() + " received a list of trainings");
            }
        }

    }

    private void statisticsResponse(Message message, User user) {
        String text = message.getText().trim().toLowerCase();
        switch (user.getCondition()) {
            case 25 -> {
                user.setCondition(26);
                String textToSend = """
                        Specify the number of recent trainings for which you want to get statistics and the training parameter for which statistics will be generated. Data entry format:
                                                  
                        The number of trainings (for example 7)
                        Parameter (distance/speed/heart rate/duration/calories)
                                                  
                        You are in statistics mode, click "exit" if you want to exit""";

                SendMessage sendMessage = new SendMessage(String.valueOf(user.getChatId()), textToSend);
                verticalKeyboard(sendMessage, new String[]{"exit"});
                executor(sendMessage);
            }
            case 26 -> {
                String[] strings = text.split("\n");
                String strUrl;
                SendPhoto sendPhoto = new SendPhoto();
                try {
                    sendPhoto.setChatId(user.getChatId());
                    strUrl = generateChartUrl(user.getTrainings()
                            .stream()
                            .sorted(Comparator.comparing(Training::getDate))
                            .limit(Long.parseLong(strings[0]))
                            .toList(), strings[1]);
                } catch (Exception e) {
                    sendMessage(user.getChatId(), ERROR_TEXT + e.getMessage() + TRY_AGAIN_TEXT);
                    log.error(ERROR_TEXT + e.getMessage());
                    return;
                }
                try {
                    sendPhoto.setPhoto(new InputFile(strUrl));
                } catch (Exception e) {
                    sendMessage(user.getChatId(), ERROR_TEXT + e.getMessage() + TRY_AGAIN_TEXT);
                    log.error(ERROR_TEXT + e.getMessage());
                    return;
                }
                try {
                    execute(sendPhoto);
                } catch (TelegramApiException e) {
                    sendMessage(user.getChatId(), ERROR_TEXT + e.getMessage() + TRY_AGAIN_TEXT);
                    log.error(ERROR_TEXT + e.getMessage());
                    return;
                }
                log.info("User " + user.getFirstName() + " received statistics by URL: " + strUrl);
            }
        }
        userRepository.save(user);
    }

    private static void verticalKeyboard(SendMessage message, String[] args) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        for (String arg : args) {
            row.add(arg);
            keyboardRows.add(row);
            row = new KeyboardRow();
        }

        keyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(keyboardMarkup);
    }

    private void executor(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            sendMessage(Long.parseLong(message.getChatId()), ERROR_TEXT + e.getMessage() + TRY_AGAIN_TEXT);
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private static void setTrainingByText(User user, String text, Training training) throws ParseException {
        String[] strings = text.trim().toLowerCase().split("\n");

        for (int i = 0; i < strings.length; i++) {
            strings[i] = strings[i].trim();
        }

        training.setTrainingType(strings[0]);
        training.setDuration(Integer.parseInt(strings[1]));
        training.setDate(convertStringToTimestamp(strings[2]));

        try {
            training.setAverageHeartRate(Integer.parseInt(strings[3]));
        } catch (NumberFormatException e) {
            training.setAverageHeartRate(null);
        }

        try {
            training.setDistance(Integer.parseInt(strings[4]));
            training.setSpeed(calculateSpeed(training.getDistance(), training.getDuration()));
        } catch (Exception e) {
            training.setDistance(null);
            training.setSpeed(null);
        }

        try {
            //Link to the source: http://frs24.ru/st/kalkulator-rashoda-kalorij-po-pulsu/
            int calories = (int) Math.round(0.014 * user.getWeight() * training.getDuration() *
                    (0.12 * training.getAverageHeartRate() - 7));
            training.setCalories(calories);
        } catch (Exception e) {
            training.setCalories(null);
        }
    }

    private void updateData(User user) {
        user.setCondition(0);
        userRepository.save(user);
        sendMessage(user.getChatId(), "Your data was update.");
        startCommandReceived(user.getChatId());
    }

    public static double calculateSpeed(double distanceMeters, double timeMinutes) {
        double timeHours = timeMinutes / 60;
        double distanceKilometers = distanceMeters / 1000;
        return distanceKilometers / timeHours;
    }

    public static Timestamp convertStringToTimestamp(String dateString) throws ParseException {
        String pattern = "dd.MM.yy/HH.mm";
        SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
        Date parsedDate = dateFormat.parse(dateString);
        return new Timestamp(parsedDate.getTime());
    }

    public static String convertTimestampToString(Timestamp timestamp) {
        String pattern = "dd.MM.yy/HH.mm";
        SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
        return dateFormat.format(timestamp);
    }

    public static String generateChartUrl(List<Training> trainingList, String dataType) {
        StringBuilder dataBuilder = new StringBuilder();
        StringBuilder labelsBuilder = new StringBuilder();

        int minValue = Integer.MAX_VALUE;
        int maxValue = Integer.MIN_VALUE;

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy");

        for (Training training : trainingList) {
            // Получение значения данных в зависимости от типа
            int dataValue = switch (dataType) {
                case "distance" -> training.getDistance() == null ? 0 : training.getDistance();
                case "speed" -> training.getSpeed() == null ? 0 : training.getSpeed().intValue();
                case "heart rate" -> training.getAverageHeartRate() == null ? 0 : training.getAverageHeartRate();
                case "duration" -> training.getDuration();
                case "calories" -> training.getCalories() == null ? 0 : training.getCalories();
                default -> throw new IllegalArgumentException("Unsupported data type: " + dataType);
            };

            // Добавление значения данных и метки
            dataBuilder.append(dataValue).append(",");
            labelsBuilder.append(convertTimestampToString(training.getDate())).append("|");

            // Обновление минимального и максимального значения
            if (dataValue < minValue) {
                minValue = dataValue;
            }
            if (dataValue > maxValue) {
                maxValue = dataValue;
            }
        }

        // Удаление последней запятой у каждого билдера
        removeTrailingComma(dataBuilder);
        removeTrailingComma(labelsBuilder);

        // Кодирование данных для URL-параметров
        String data = URLEncoder.encode(dataBuilder.toString(), StandardCharsets.UTF_8);
        String labels = URLEncoder.encode(labelsBuilder.toString(), StandardCharsets.UTF_8);

        // Формирование URL-адреса графика
        return "https://chart.googleapis.com/chart" +
                "?cht=bvg" +                      // Тип графика: столбчатая диаграмма (столбики)
                "&chs=800x300" +                 // Размер графика (ширина x высота)
                "&chd=t:" + data +               // Данные
                "&chxt=x,y" +                    // Оси X и Y
                "&chxl=0:|" + labels +           // Метки по оси X
                "&chdl=" + dataType +            // Легенда
                "&chco=FF0000" +                 // Цвет графика (красный)
                "&chxr=1," + 0 + "," + maxValue +// Диапазон значений по оси Y
                "&chds=" + 0 + "," + maxValue +  // Минимальное и максимальное значения по оси Y
                "&chtt=" + dataType +            // Заголовок графика
                "&chg=10,10,1,1" +               // Параметры сетки: длина и промежутки линий
                "&chbh=a";                       // Ширина столбцов графика
        // Параметры сетки: длина и промежутки линий
    }

    private static void removeTrailingComma(StringBuilder input) {
        if (input.length() > 0) {
            input.setLength(input.length() - 1);
        }
    }


    private void settingsCommandResponse(User user) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> level1 = new ArrayList<>();

        var button1 = new InlineKeyboardButton();
        button1.setText("Set weight");
        button1.setCallbackData(WEIGHT_BUTTON);
        level1.add(button1);

        var button2 = new InlineKeyboardButton();
        button2.setText("Set height");
        button2.setCallbackData(HEIGHT_BUTTON);
        level1.add(button2);

        var button3 = new InlineKeyboardButton();
        button3.setText("Set age");
        button3.setCallbackData(AGE_BUTTON);
        level1.add(button3);

        rows.add(level1);

        // Level 2 Buttons
        List<InlineKeyboardButton> level2 = new ArrayList<>();

        var button4 = new InlineKeyboardButton();
        button4.setText("My data");
        button4.setCallbackData(MYDATA_BUTTON);
        level2.add(button4);

        var button5 = new InlineKeyboardButton();
        button5.setText("Delete my data");
        button5.setCallbackData(DELETE_BUTTON);
        level2.add(button5);

        var button6 = new InlineKeyboardButton();
        button6.setText("Connect a third-party API");
        button6.setCallbackData(API_BUTTON);
        level2.add(button6);

        rows.add(level2);
        inlineKeyboardMarkup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(user.getChatId());
        message.setText("Select the parameter you want to change.");
        message.setReplyMarkup(inlineKeyboardMarkup);
        executor(message);

        log.info("Settings sent to the user " + user.getFirstName());
    }

    private void executeEditMessageText(int messageId, long chatId, EditMessageText message, String text) {
        message.setChatId(chatId);
        message.setText(text);
        message.setMessageId(messageId);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            sendMessage(chatId, ERROR_TEXT + e.getMessage() + TRY_AGAIN_TEXT);
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void deleteData(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Do you really want to delete your data? THE DATA WILL BE IRRETRIEVABLY LOST!");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLIne = new ArrayList<>();

        var deleteButton = new InlineKeyboardButton();
        deleteButton.setText("Yes");
        deleteButton.setCallbackData(FINALLY_DELETE_BUTTON);
        rowInLIne.add(deleteButton);

        var noButton = new InlineKeyboardButton();
        noButton.setText("NO");
        noButton.setCallbackData(NO_BUTTON);
        rowInLIne.add(noButton);

        rowsInLine.add(rowInLIne);
        markupInline.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInline);

        executor(message);
    }

    private boolean deleteDataCommandReceived(long chatId) {
        Optional<User> userOptional = userRepository.findById(chatId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            userRepository.delete(user);
            log.info("User delete data " + user.getFirstName());
            return true;
        } else {
            sendMessage(chatId, "Your data is not in the storage.");
            return false;
        }
    }

    private void myDataCommandReceived(Message message) {
        Optional<User> userOptional = userRepository.findById(message.getChatId());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            sendMessage(message.getChatId(), user.toString());
            log.info("User get data " + user.getFirstName());
        } else {
            sendMessage(message.getChatId(), "Your data is not in the storage.");
        }
    }

    private void registerUser(Message message) {
        String answer = EmojiParser.parseToUnicode("Hi, " + message.getChat().getFirstName() + ", welcome to the training tracking app!");
        sendMessage(message.getChatId(), answer);

        if (userRepository.findById(message.getChatId()).isEmpty()) {
            long chatId = message.getChatId();
            Chat chat = message.getChat();

            User user = new User();
            user.setChatId(chatId);
            user.setUserName(chat.getUserName());
            user.setFirstName(chat.getFirstName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            user.setCondition(0);

            userRepository.save(user);
            log.info("User saves " + user.getFirstName());
            sendMessage(message.getChatId(), "We have created your profile in the database, " +
                    "now you can save your trainings and use all the functionality of the service. " +
                    "But we need some more of your data.");
        } else {
            sendMessage(message.getChatId(), "You are already registered in the system.");
        }
    }

    private void startCommandReceived(long chatId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> level1 = new ArrayList<>();
        User user = userRepository.findByChatId(chatId);
        boolean flag = false;

        if (user.getWeight() == null) {
            flag = true;
            var button1 = new InlineKeyboardButton();
            button1.setText("Set weight");
            button1.setCallbackData(WEIGHT_BUTTON);
            level1.add(button1);
        }
        if (user.getHeight() == null) {
            flag = true;
            var button2 = new InlineKeyboardButton();
            button2.setText("Set height");
            button2.setCallbackData(HEIGHT_BUTTON);
            level1.add(button2);
        }
        if (user.getAge() == null) {
            flag = true;
            var button3 = new InlineKeyboardButton();
            button3.setText("Set age");
            button3.setCallbackData(AGE_BUTTON);
            level1.add(button3);
        }

        rows.add(level1);
        inlineKeyboardMarkup.setKeyboard(rows);

        if (flag) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Please enter your parameters for more accurate tracking");
            message.setReplyMarkup(inlineKeyboardMarkup);
            executor(message);
        } else {
            String answer = EmojiParser.parseToUnicode("Great, now we can get started. To record a new training, send the command /newtraining");
            sendMessage(chatId, answer);
        }


        log.info("Replied to user " + user.getFirstName());

    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);

        executor(message);
    }
}
