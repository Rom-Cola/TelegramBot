package com.myproject;

import com.myproject.model.Task;
import com.myproject.model.TaskStatus;
import com.myproject.model.User;
import com.myproject.model.UserRole;
import com.myproject.service.TaskService;
import com.myproject.service.UserService;
import com.myproject.service.impl.TaskServiceImpl;
import com.myproject.service.impl.UserServiceImpl;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TaskTrackerBot extends TelegramLongPollingBot {

    private final TaskService taskService = new TaskServiceImpl();
    private final UserService userService = new UserServiceImpl();

    private String botUsername;
    private String botToken;
    private final Map<Long, UserRole> userRoles = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> taskData = new ConcurrentHashMap<>();
    private final Map<Long, TaskCreationState> taskCreationState = new ConcurrentHashMap<>();



    {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("telegram.properties")) {
            if (input == null){
                throw new RuntimeException("Не вдалося знайти файл telegram.properties в classpath");
            }
            properties.load(input);
            this.botUsername = properties.getProperty("telegram.bot.username");
            this.botToken = properties.getProperty("telegram.bot.token");
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new RuntimeException("Помилка при завантаженні налаштувань", ex);
        }
        createDefaultUsers();
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
    private void createDefaultUsers() {
        if(userService.getAllUsers().isEmpty()) {
            User adminUser = new User("Admin User", UserRole.ADMIN, 1L);
            userService.createUser(adminUser);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            String[] parts = messageText.split(" ", 2);

            if (parts[0].equals("/start")) {
                handleStartCommand(chatId,messageText);
                return;
            }
            if (taskCreationState.containsKey(chatId)) {
                handleTaskCreationMessage(chatId, messageText);
                return;
            }
            handleCommand(chatId,parts);
        }
        else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }
    private void handleStartCommand(Long chatId, String messageText) {
        Optional<User> existingUser = userService.getUserByChatId(chatId);
        if (existingUser.isPresent()) {
            sendMessage(chatId, "Вітаю, я бот для моніторингу виконання завдань!", createCommandKeyboard(getUserRole(chatId)));
        } else{
            sendMessage(chatId, "Будь ласка оберіть свою роль:", createRoleKeyboard());
        }
    }
    private void handleTaskCreationMessage(Long chatId, String messageText) {
        TaskCreationState state = taskCreationState.get(chatId);
        Map<String, String> data = taskData.getOrDefault(chatId, new HashMap<>());
        switch (state) {
            case TITLE:
                data.put("title", messageText);
                taskData.put(chatId, data);
                taskCreationState.put(chatId, TaskCreationState.DESCRIPTION);
                sendMessage(chatId, "Введіть опис завдання:");
                break;
            case DESCRIPTION:
                data.put("description", messageText);
                taskData.put(chatId, data);
                taskCreationState.put(chatId, TaskCreationState.DEADLINE);
                sendMessage(chatId, "Введіть дедлайн завдання (yyyy-MM-dd):");
                break;
            case DEADLINE:
                data.put("deadline", messageText);
                taskData.put(chatId, data);
                taskCreationState.put(chatId, TaskCreationState.TEACHER_ID);
                sendMessage(chatId, "Введіть ID викладача:");
                break;
            case TEACHER_ID:
                data.put("teacherId", messageText);
                taskData.put(chatId, data);
                createTaskFromData(chatId, data);
                taskCreationState.remove(chatId);
                taskData.remove(chatId);
                break;
        }
    }

    private void createTaskFromData(Long chatId, Map<String, String> data) {
        try {
            String title = data.get("title");
            String description = data.get("description");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate deadlineDate = LocalDate.parse(data.get("deadline"), dateFormatter);
            Date deadline = java.sql.Date.valueOf(deadlineDate);
            Long taskUserId = Long.parseLong(data.get("teacherId"));
            Optional<User> userOptional = userService.getUserById(taskUserId);
            if (userOptional.isPresent()){
                User user = userOptional.get();
                Task newTask = new Task(title, description, deadline, TaskStatus.PENDING, user);
                taskService.createTask(newTask);
                sendMessage(chatId, "Завдання створено з id: " + newTask.getId());
            } else {
                sendMessage(chatId,"Користувача з id: " + taskUserId + " не знайдено");
            }
        } catch (DateTimeParseException e) {
            sendMessage(chatId, "Невірний формат дати. Будь ласка використовуйте рік-місяць-день");
        } catch (NumberFormatException e) {
            sendMessage(chatId,"Невірний ID користувача. Будь ласка, введіть коректний номер");
        }
    }
    private void registerUser(Long chatId, String name, UserRole role) {
        Optional<User> userOptional = userService.getUserByChatId(chatId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setName(name);
            user.setRole(role);
            userService.updateUser(user);
        } else {
            User newUser = new User(name, role, chatId);
            userService.createUser(newUser);
        }
    }
    private void handleCommand(Long chatId, String[] parts) {
        UserRole userRole = getUserRole(chatId);
        Long userId = getUserIdFromChatId(chatId);

        switch (parts[0]) {
            case "/help":
                sendHelpMessage(chatId, userRole);
                break;
            case "/addTask":
                if (userRole == UserRole.ADMIN) {
                    startAddTaskDialog(chatId);
                }else {
                    sendMessage(chatId, "У вас немає доступу до цієї команди");
                }
                break;
            case "/myTasks":
                listMyTasks(chatId, userId);
                break;
            case "/listTasks":
                if (userRole == UserRole.ADMIN) {
                    listTasks(chatId);
                }else {
                    sendMessage(chatId, "У вас немає доступу до цієї команди");
                }
                break;
            case "/updateTask":
                updateTask(chatId, parts.length > 1 ? parts[1] : null,userRole, userId);
                break;
            case "/listUsers":
                if (userRole == UserRole.ADMIN) {
                    listUsers(chatId);
                }else {
                    sendMessage(chatId, "У вас немає доступу до цієї команди");
                }
                break;
            case "/taskDetails":
                taskDetails(chatId, parts.length > 1 ? parts[1]: null);
                break;
            default:
                sendMessage(chatId, "Невідома команда!");
                break;
        }
    }
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        try {
            CallbackData callback = CallbackData.fromString(callbackData);

            if (callback == CallbackData.TEACHER || callback == CallbackData.ADMIN) {
                handleRoleSelection(chatId,callback);
                return;
            }
            UserRole userRole = getUserRole(chatId);
            Long userId = getUserIdFromChatId(chatId);

            switch (callback) {
                case ADD_TASK:
                    if (userRole == UserRole.ADMIN) {
                        startAddTaskDialog(chatId);
                    }else {
                        sendMessage(chatId, "У вас немає доступу до цієї команди");
                    }
                    break;
                case MY_TASKS:
                    listMyTasks(chatId, userId);
                    break;
                case LIST_TASKS:
                    handleListTasksButton(chatId,userRole);
                    break;
                case UPDATE_TASK:
                    sendMessage(chatId, "Будь ласка вкажіть id завдання та новий статус у форматі  '/updateTask <id_завдання> <новий_статус>'");
                    break;
                case LIST_USERS:
                    handleListUsersButton(chatId,userRole);
                    break;
                case TASK_DETAILS:
                    taskDetails(chatId,"Будь ласка вкажіть id завдання ");
                    break;
                case HELP:
                    sendHelpMessage(chatId, userRole);
                    break;
            }
        }catch (IllegalArgumentException e) {
            sendMessage(chatId, "Невідома команда!");
        }
    }
    private void handleRoleSelection(Long chatId, CallbackData callback) {
        sendMessage(chatId,"Будь ласка, введіть своє ім'я:");
        userRoles.put(chatId, callback == CallbackData.ADMIN ? UserRole.ADMIN : UserRole.TEACHER);
        return;
    }
    private void handleListUsersButton(Long chatId, UserRole userRole){
        if (userRole == UserRole.ADMIN) {
            listUsers(chatId);
        }else {
            sendMessage(chatId, "У вас немає доступу до цієї команди");
        }
    }
    private void handleListTasksButton(Long chatId, UserRole userRole) {
        if (userRole == UserRole.ADMIN) {
            listTasks(chatId);
        }else {
            sendMessage(chatId, "У вас немає доступу до цієї команди");
        }
    }
    private void handleAddTaskButton(Long chatId, UserRole userRole) {
        if (userRole == UserRole.ADMIN) {
            startAddTaskDialog(chatId);
        }else {
            sendMessage(chatId, "У вас немає доступу до цієї команди");
        }
    }
    private void startAddTaskDialog(Long chatId) {
        taskCreationState.put(chatId, TaskCreationState.TITLE);
        sendMessage(chatId, "Введіть заголовок завдання:");
    }
    private InlineKeyboardMarkup createRoleKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton teacherButton =  new InlineKeyboardButton("Викладач");
        teacherButton.setCallbackData(CallbackData.TEACHER.getCallbackData());
        InlineKeyboardButton adminButton =  new InlineKeyboardButton("Завідувач кафедри");
        adminButton.setCallbackData(CallbackData.ADMIN.getCallbackData());
        row1.add(teacherButton);
        row1.add(adminButton);
        keyboard.add(row1);
        markup.setKeyboard(keyboard);
        return markup;
    }


    private Long getUserIdFromChatId(Long chatId) {
        Optional<User> user = userService.getUserByChatId(chatId);
        return user.map(User::getId).orElse(null);
    }
    private void sendHelpMessage(Long chatId, UserRole userRole) {
        String helpText;
        if (userRole == UserRole.ADMIN) {
            helpText = "Доступні команди:\n"
                    + "/start - Розпочати роботу з ботом\n"
                    + "/addTask - Створити завдання\n"
                    + "/myTasks - Переглянути мої завдання\n"
                    + "/listTasks - Переглянути всі завдання\n"
                    + "/updateTask <id_завдання> <новий_статус> - Оновити статус завдання\n"
                    + "/listUsers - Переглянути всіх користувачів\n"
                    + "/taskDetails <id> - Переглянути деталі завдання\n"
                    + "/help - Показати це повідомлення";
        }else {
            helpText = "Доступні команди:\n"
                    + "/start - Розпочати роботу з ботом\n"
                    + "/myTasks - Переглянути мої завдання\n"
                    + "/updateTask <id_завдання> <новий_статус> - Оновити статус завдання\n"
                    + "/taskDetails <id> - Переглянути деталі завдання\n"
                    + "/help - Показати це повідомлення";
        }
        sendMessage(chatId, helpText);
    }

    private UserRole getUserRole(Long chatId) {
        Long userId = getUserIdFromChatId(chatId);
        if (userId != null) {
            Optional<User> userOptional = userService.getUserById(userId);
            return userOptional.map(User::getRole).orElse(UserRole.TEACHER);
        } else if (userRoles.containsKey(chatId)) {
            return userRoles.get(chatId);
        } else{
            return UserRole.TEACHER;
        }
    }
    private InlineKeyboardMarkup createCommandKeyboard(UserRole userRole) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton myTasksButton = new InlineKeyboardButton("Мої завдання");
        myTasksButton.setCallbackData(CallbackData.MY_TASKS.getCallbackData());
        row1.add(myTasksButton);
        if (userRole == UserRole.ADMIN) {
            InlineKeyboardButton addTaskButton = new InlineKeyboardButton("Додати завдання");
            addTaskButton.setCallbackData(CallbackData.ADD_TASK.getCallbackData());
            row1.add(addTaskButton);
        }
        keyboard.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        if (userRole == UserRole.ADMIN) {
            InlineKeyboardButton listTasksButton = new InlineKeyboardButton("Список завдань");
            listTasksButton.setCallbackData(CallbackData.LIST_TASKS.getCallbackData());
            row2.add(listTasksButton);
        }
        InlineKeyboardButton updateTaskButton = new InlineKeyboardButton("Оновити завдання");
        updateTaskButton.setCallbackData(CallbackData.UPDATE_TASK.getCallbackData());
        row2.add(updateTaskButton);
        keyboard.add(row2);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        if (userRole == UserRole.ADMIN) {
            InlineKeyboardButton listUsersButton = new InlineKeyboardButton("Список користувачів");
            listUsersButton.setCallbackData(CallbackData.LIST_USERS.getCallbackData());
            row3.add(listUsersButton);
        }

        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton taskDetailsButton =  new InlineKeyboardButton("Деталі завдання");
        taskDetailsButton.setCallbackData(CallbackData.TASK_DETAILS.getCallbackData());
        InlineKeyboardButton helpButton =  new InlineKeyboardButton("Допомога");
        helpButton.setCallbackData(CallbackData.HELP.getCallbackData());
        row4.add(taskDetailsButton);
        row4.add(helpButton);
        keyboard.add(row4);
        markup.setKeyboard(keyboard);
        return markup;
    }

    private void taskDetails(Long chatId, String taskIdDetails) {
        if(taskIdDetails == null) {
            sendMessage(chatId,"Будь ласка вкажіть id завдання");
            return;
        }
        try {
            Long taskId = Long.parseLong(taskIdDetails);
            Optional<Task> taskOptional = taskService.getTaskById(taskId);
            if (taskOptional.isPresent()){
                Task task = taskOptional.get();
                sendMessage(chatId, String.format("Завдання id: %d, заголовок: %s, опис: %s, статус: %s ",
                        task.getId(), task.getTitle(), task.getDescription(), task.getStatus()));
            }else {
                sendMessage(chatId, "Завдання з id " + taskId + " не знайдено");
            }
        }catch (NumberFormatException e) {
            sendMessage(chatId,"Невірний ID завдання");
        }
    }
    private void listUsers(Long chatId) {
        List<User> users = userService.getAllUsers();
        if(users.isEmpty()) {
            sendMessage(chatId, "Користувачів не знайдено");
        } else {
            String userList = users.stream()
                    .map(user -> String.format("Користувач id: %d, ім'я: %s, роль: %s",
                            user.getId(), user.getName(), user.getRole()))
                    .collect(Collectors.joining("\n"));
            sendMessage(chatId, userList);
        }
    }


    private void updateTask(Long chatId, String taskDetails, UserRole userRole, Long userId) {
        if (taskDetails == null) {
            sendMessage(chatId,"Будь ласка вкажіть id завдання та новий статус у форматі  '/updateTask <id_завдання> <новий_статус>'");
            return;
        }
        String[] detailsParts = taskDetails.split(" ");
        if (detailsParts.length != 2) {
            sendMessage(chatId,"Невірний формат. Будь ласка вкажіть  <id_завдання> <новий_статус> ");
            return;
        }
        try {
            Long taskId = Long.parseLong(detailsParts[0]);
            String newStatus = detailsParts[1].toUpperCase();

            Optional<Task> optionalTask = taskService.getTaskById(taskId);
            if (optionalTask.isPresent()) {
                Task task = optionalTask.get();
                if(userRole == UserRole.ADMIN || task.getUser().getId().equals(userId)) {
                    try {
                        task.setStatus(TaskStatus.valueOf(newStatus));
                        taskService.updateTask(task);
                        sendMessage(chatId,"Статус завдання " + taskId + " оновлено на " + newStatus);
                    } catch (IllegalArgumentException e) {
                        sendMessage(chatId,"Невірний статус завдання. Будь ласка вкажіть коректний статус");
                    }
                }else {
                    sendMessage(chatId,"У вас немає прав змінювати статус цього завдання");
                }

            } else {
                sendMessage(chatId,"Завдання з id: " + taskId + " не знайдено");
            }
        }
        catch (NumberFormatException e) {
            sendMessage(chatId,"Невірний ID завдання. Будь ласка, введіть коректний номер");
        }
    }
    private void listTasks(Long chatId) {
        List<Task> tasks = taskService.getAllTasks();
        if (tasks.isEmpty()){
            sendMessage(chatId, "Завдань не знайдено");
        } else {
            String taskList = tasks.stream()
                    .map(task -> String.format("Завдання id: %d, заголовок: %s, опис: %s, статус: %s ",
                            task.getId(), task.getTitle(), task.getDescription(), task.getStatus()))
                    .collect(Collectors.joining("\n"));
            sendMessage(chatId, taskList);
        }
    }

    private void listMyTasks(Long chatId, Long userId) {
        if (userId != null) {
            Optional<User> userOptional =  userService.getUserById(userId);
            if(userOptional.isPresent()) {
                User user = userOptional.get();
                List<Task> tasks = taskService.getTasksByUser(user);
                if (tasks.isEmpty()) {
                    sendMessage(chatId, "Завдань для користувача " + user.getName() + " не знайдено");
                } else {
                    String taskList = tasks.stream()
                            .map(task -> String.format("Завдання id: %d, заголовок: %s, опис: %s, статус: %s ",
                                    task.getId(), task.getTitle(), task.getDescription(), task.getStatus()))
                            .collect(Collectors.joining("\n"));
                    sendMessage(chatId, taskList);
                }
            } else {
                sendMessage(chatId, "Користувача не знайдено");
            }
        } else {
            sendMessage(chatId, "Користувача не знайдено");
        }
    }
    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }
    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        if (markup != null) {
            message.setReplyMarkup(markup);
        }
        try {
            this.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    public enum CallbackData {
        TEACHER("teacher"),
        ADMIN("admin"),
        ADD_TASK("addTask"),
        MY_TASKS("myTasks"),
        LIST_TASKS("listTasks"),
        UPDATE_TASK("updateTask"),
        LIST_USERS("listUsers"),
        TASK_DETAILS("taskDetails"),
        HELP("help");

        private final String callbackData;

        CallbackData(String callbackData) {
            this.callbackData = callbackData;
        }
        public String getCallbackData() {
            return callbackData;
        }

        public static CallbackData fromString(String callbackData) {
            for (CallbackData value : CallbackData.values()) {
                if (value.getCallbackData().equals(callbackData)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("No such callback data: " + callbackData);
        }
    }
    private enum TaskCreationState {
        TITLE,
        DESCRIPTION,
        DEADLINE,
        TEACHER_ID
    }
}