package com.telegram.freelkee.workoutrunbot.service;

import com.telegram.freelkee.workoutrunbot.config.BotConfig;
import com.telegram.freelkee.workoutrunbot.model.Training;
import com.telegram.freelkee.workoutrunbot.model.User;
import com.telegram.freelkee.workoutrunbot.repository.AdsRepository;
import com.telegram.freelkee.workoutrunbot.repository.TrainingRepository;
import com.telegram.freelkee.workoutrunbot.repository.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrainingRepository trainingRepository;

    final BotConfig config;

    static final String HELP_TEXT = """
            This bot is created to help you track your workouts and running activities.

            You can execute commands from the main menu on the left or by typing a command:

            Type /start to see a welcome message.

            Type /newworkout to add a new workout to your diary.
                        
            Type /myworkouts to see your workout history.

            Type /statistics to view your workout statistics.

            Type /help to see this message again.

            Type /settings to customize your preferences.
            """;

    static final String NEWWORKOUT_TEXT = """
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
    static final String DELETE_BUTTON = "DELETE_BUTTON";
    static final String WEIGHT_BUTTON = "WEIGHT_BUTTON";
    static final String HEIGHT_BUTTON = "HEIGHT_BUTTON";
    private static final String AGE_BUTTON = "AGE_BUTTON";
    private static final String MYDATA_BUTTON = "MYDATA_BUTTON";
    private static final String API_BUTTON = "API_BUTTON";
    private static final String UPDATE_TR_BUTTON = "UPDATE_TR_BUTTON";
    private static final String DELETE_TR_BUTTON = "DELETE_TR_BUTTON";


    static final String ERROR_TEXT = "Error occurred: ";

    public WorkoutBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/newworkout", "record a new workout"));
        listOfCommands.add(new BotCommand("/myworkouts", "get your data workouts"));
        listOfCommands.add(new BotCommand("/statistics", "get your stats"));
        listOfCommands.add(new BotCommand("/help", "info how to use this bot"));
        listOfCommands.add(new BotCommand("/settings", "set your preferences"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }


    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();

        if (update.hasMessage() && message.hasText()) {
            User user = userRepository.findByChatId(message.getChatId());
            String messageText = message.getText();
            long chatId = message.getChatId();

            if (messageText.contains("/send") && config.getOwnerId() == chatId) {
                var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                var users = userRepository.findAll();
                for (User user1 : users) {
                    sendMessage(user1.getChatId(), textToSend);
                }
            } else if (user == null || user.getCondition() == 0) {
                switch (messageText.trim()) {
                    case "/start" -> {
                        registerUser(message);
                        startCommandReceived(chatId);
                    }
                    case "/help" -> sendMessage(chatId, HELP_TEXT);
                    case "/mydata" -> myDataCommandReceived(message);
                    case "/deletedata" -> deleteData(chatId);
                    case "/newworkout" -> {
                        assert user != null;
                        user.setCondition(1);
                        userRepository.save(user);
                        newWorkoutCommandReceived(message, user);
                    }
                    case "/myworkouts" -> {
                        assert user != null;
                        user.setCondition(20);
                        userRepository.save(user);
                        myWorkoutsCommandReceived(message, user);
                    }
                    case "/settings" -> {
                        assert user != null;
                        settingsCommandRecieved(user);
                    }
                    case "/statistics" -> {
                        assert user != null;
                        user.setCondition(25);
                        userRepository.save(user);
                        responseForStatistics(message, user);
                    }

                    default -> sendMessage(chatId, "Sorry,command was not recognized");
                }
            } else if (user.getCondition() > 0 && user.getCondition() <= 4) {
                //new workout
                if (messageText.equals("exit")) {
                    user.setCondition(0);
                    userRepository.save(user);
                } else
                    newWorkoutCommandReceived(message, user);
            } else if (user.getCondition() >= 5 && user.getCondition() <= 9) {
                //update workout
                if (messageText.equals("exit")) {
                    user.setCondition(0);
                    userRepository.save(user);
                } else
                    updateWorkoutCommandReceived(message, user);
            } else if (user.getCondition() >= 10 && user.getCondition() <= 19) {
                //settings
                if (messageText.equals("exit")) {
                    user.setCondition(0);
                    userRepository.save(user);
                } else
                    setting(message, user);
            } else if (user.getCondition() >= 20 && user.getCondition() <= 24) {
                //my workouts
                if (messageText.equals("exit")) {
                    user.setCondition(0);
                    userRepository.save(user);
                } else
                    myWorkoutsCommandReceived(message, user);
            } else if (user.getCondition() >= 25 && user.getCondition() <= 29) {
                if (messageText.equals("exit")) {
                    user.setCondition(0);
                    userRepository.save(user);
                } else
                    responseForStatistics(message, user);
            }
        } else if (update.hasCallbackQuery()) {
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

            } else if (callbackData.equals(DELETE_BUTTON)) {
                if (deleteDataCommandReceived(chatId)) {
                    String text = "You deleted your data";
                    executeEditMessageText((int) messageId, chatId, editMessageText, text);
                }
            } else if (callbackData.equals(WEIGHT_BUTTON)) {
                user.setCondition(10);
                String text = "Enter your weight in kilograms";
                executeEditMessageText((int) messageId, chatId, editMessageText, text);
                userRepository.save(user);

            } else if (callbackData.equals(HEIGHT_BUTTON)) {
                user.setCondition(11);
                String text = "Enter your height in centimeters";
                executeEditMessageText((int) messageId, chatId, editMessageText, text);
                userRepository.save(user);

            } else if (callbackData.equals(AGE_BUTTON)) {
                user.setCondition(12);
                String text = "Enter your age in years";
                executeEditMessageText((int) messageId, chatId, editMessageText, text);
                userRepository.save(user);

            } else if (callbackData.equals(MYDATA_BUTTON)) {
                user.setCondition(0);
                String text = "This is all the data we have about you:\n" + user;
                executeEditMessageText((int) messageId, chatId, editMessageText, text);
                userRepository.save(user);

            } else if (callbackData.equals("DEL")) {
                user.setCondition(14);
                deleteData(chatId);
                userRepository.save(user);

            } else if (callbackData.equals(API_BUTTON)) {
                user.setCondition(0);
                String text = "This option is in development.";
                executeEditMessageText((int) messageId, chatId, editMessageText, text);
                userRepository.save(user);

            } else if (callbackData.startsWith(UPDATE_TR_BUTTON)) {
                Long id = Long.parseLong(callbackData.split(" ")[1]);
                Training training = trainingRepository.findById(id).orElse(null);
                if (training != null) {
                    user.setCondition(5);
                    String text = "This is your training data, copy it and send the corrected version.\n\n" +
                            training.getTrainingType() + "\n" +
                            training.getDuration() + "\n" +
                            new SimpleDateFormat("dd.MM.yyyy/HH.mm").format(training.getDate()) + "\n" +
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
    }

    private void responseForStatistics(Message message, User user) {
        switch (user.getCondition()) {
            case 25 -> {
                user.setCondition(26);
                sendMessage(user.getChatId(), """
                        Specify the number of recent workouts for which you want to get statistics and the training parameter for which statistics will be generated. Data entry format:
                        The number of workouts as a number (for example 7)
                        Parameter (for example "duration")""");
            }
            case 26 -> {
                String[] strings = message.getText().split("\n");

                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(user.getChatId());
                sendPhoto.setPhoto(new InputFile(generateChartUrl(user.getTrainings().stream().limit(Long.parseLong(strings[0])).toList(), strings[1])));

                try {
                    execute(sendPhoto);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                user.setCondition(0);
            }

        }
        userRepository.save(user);

    }

    public static String generateChartUrl(List<Training> trainingList, String dataType) {
        StringBuilder dataBuilder = new StringBuilder();
        StringBuilder labelsBuilder = new StringBuilder();

        int minValue = Integer.MAX_VALUE;
        int maxValue = Integer.MIN_VALUE;

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy/HH.mm");

        for (Training training : trainingList) {
            // Получение значения данных в зависимости от типа
            int dataValue = switch (dataType) {
                case "distance" -> training.getDistance() == null ? 0 : training.getDistance();
                case "speed" -> training.getSpeed() == null ? 0 : training.getSpeed().intValue();
                case "heartRate" -> training.getAverageHeartRate() == null ? 0 : training.getAverageHeartRate();
                case "duration" -> training.getDuration();
                case "calories" -> training.getCalories() == null ? 0 : training.getCalories();
                default -> throw new IllegalArgumentException("Unsupported data type: " + dataType);


            };

            // Добавление значения данных и метки
            dataBuilder.append(dataValue).append(",");
            labelsBuilder.append(dateFormat.format(training.getDate())).append("|");

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
                "?cht=lc" +                  // Тип графика: линейный
                "&chs=600x300" +             // Размер графика (ширина x высота)
                "&chd=t:" + data +           // Данные
                "&chxt=x,y" +                // Оси X и Y
                "&chxl=0:|" + labels +       // Метки по оси X
                "&chdl=" + dataType +        // Легенда
                "&chco=FF0000" +             // Цвет графика (красный)
                "&chxr=1," + minValue + "," + maxValue +  // Диапазон значений по оси Y
                "&chds=" + minValue + "," + maxValue +    // Минимальное и максимальное значения по оси Y
                "&chtt=" + dataType;
    }

    private static void removeTrailingComma(StringBuilder input) {
        if (input.length() > 0) {
            input.setLength(input.length() - 1);
        }
    }

    private void updateWorkoutCommandReceived(Message message, User user) throws ParseException {
        if (user.getCondition() == 5) {
            String text = message.getText();
            Long trainingId = user.getUpdateTrainingId();

            Training training = trainingRepository.findById(trainingId).orElse(null);
//                user.trainings.remove(training);
            userRepository.save(user);
            if (training == null) {
                // Обработка случая, когда объект training не найден
                // Можно выдать сообщение об ошибке или создать новый объект Training
                // training = new Training();
            } else {
                setTrainingByText(user, text, training);

                user.setCondition(0);
                user.setUpdateTrainingId(null);
                userRepository.save(user);

                trainingRepository.save(training); // Сохранение после обновления объекта Training
                log.info("User " + user.getUserName() + " updated workout " + training.getId());
                sendMessage(message.getChatId(), "Your workout was updated.");
            }
        }
    }

    private void settingsCommandRecieved(User user) {
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
        button5.setCallbackData("DEL");
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

        log.info("Replied to user " + user.getUserName());
    }

    private void myWorkoutsCommandReceived(Message message, User user) {
        Long chatId = user.getChatId();
        String text = message.getText();
        switch (user.getCondition()) {
            case 0 -> sendMessage(chatId, "Exit from workouts.");
            case 20 -> {
                sendMessage(chatId, "How many workouts do you want to see?");
                user.setCondition(21);
                userRepository.save(user);
            }
            case 21 -> {
                int limit = Integer.parseInt(text);

                List<Training> trainings = user.getTrainings()
                        .stream()
                        .sorted(Comparator.comparing(Training::getDate).reversed())
                        .limit(limit)
                        .toList();

                if (trainings.size() == 0) {
                    sendMessage(chatId, "You don't have a record, try to record a new one using the /newworkout command");
                    user.setCondition(0);
                    userRepository.save(user);
                    return;
                }
                sendMessage(chatId, "Here are your " + trainings.size() + " workouts sorted by date");

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
            }
        }

    }

    private void setting(Message message, User user) {
        Long chatId = user.getChatId();
        String text = message.getText();
        switch (user.getCondition()) {
            case 0 -> sendMessage(chatId, "Exit from setting.");
            case 10 -> {
                user.setWeight(Integer.parseInt(text));
                updateData(user, chatId);
            }
            case 11 -> {
                user.setHeight(Integer.parseInt(text));
                updateData(user, chatId);
            }
            case 12 -> {
                user.setAge(Integer.parseInt(text));
                updateData(user, chatId);
            }
        }
    }

    private void updateData(User user, Long chatId) {
        user.setCondition(0);
        userRepository.save(user);
        sendMessage(chatId, "Your data was update.");
        startCommandReceived(chatId);
    }

    private void newWorkoutCommandReceived(Message message, User user) throws ParseException {
        Long chatId = user.getChatId();
        String text = message.getText();
        switch (user.getCondition()) {
            case 0 -> sendMessage(chatId, "Exit from creating а new Workout.");
            case 1 -> {
                sendMessage(chatId, NEWWORKOUT_TEXT);
                user.setCondition(2);
                userRepository.save(user);
            }
            case 2 -> {
                Training training = new Training();
                setTrainingByText(user, text, training);
                user.trainings.add(training);
                user.setCondition(0);
                trainingRepository.save(training);
                userRepository.save(user);
                log.info("User " + user.getUserName() + "  recorded workout " + training.getId());
                sendMessage(chatId, "Your workout was recorded.");

            }
            default -> sendMessage(chatId, "Sorry,command was not recognized.");
        }
    }

    private static void setTrainingByText(User user, String text, Training training) throws ParseException {
        String[] strings = text.split("\n");

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

    private void executeEditMessageText(int messageId, long chatId, EditMessageText message, String text) {
        message.setChatId(chatId);
        message.setText(text);
        message.setMessageId(messageId);

        try {
            execute(message);
        } catch (TelegramApiException e) {
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
        deleteButton.setCallbackData(DELETE_BUTTON);
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
            log.info("User delete data " + user.getUserName());
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
            log.info("User get data " + user.getUserName());
        } else {
            sendMessage(message.getChatId(), "Your data is not in the storage.");
        }
    }

    private void registerUser(Message message) {
        String answer = EmojiParser.parseToUnicode("Hi, " + message.getChat().getUserName() + ", welcome to the workout tracking app!");
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
            log.info("User saves " + user.getUserName());
            sendMessage(message.getChatId(), "We have created your profile in the database, " +
                    "now you can save your workouts and use all the functionality of the service. " +
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
//// Level 2 Buttons
//        List<InlineKeyboardButton> level2 = new ArrayList<>();
//
//        var button3 = new InlineKeyboardButton();
//        button3.setText("Level 2 Button 1");
//        button3.setCallbackData("level2_button1");
//        level2.add(button3);
//
//
//        var button4 = new InlineKeyboardButton();
//        button4.setText("Level 2 Button 2");
//        button4.setCallbackData("level2_button2");
//        level2.add(button4);
//
//        rows.add(level2);
        inlineKeyboardMarkup.setKeyboard(rows);

        if (flag) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Please enter your parameters for more accurate tracking");
            message.setReplyMarkup(inlineKeyboardMarkup);
            executor(message);
        } else {
            String answer = EmojiParser.parseToUnicode("Great, now we can get started. To record a new workout, send the command /newworkout");
            sendMessage(chatId, answer);
        }


        log.info("Replied to user " + user.getUserName());

    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);

        //verticalKeyboard(message, new String[]{"Test button", "Back"});

        executor(message);
    }

    private void executor(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
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
}
