package com.myproject;

import com.myproject.model.Task;
import com.myproject.model.TaskStatus;
import com.myproject.model.User;
import com.myproject.model.UserRole;
import com.myproject.service.TaskService;
import com.myproject.service.UserService;
import com.myproject.service.impl.TaskServiceImpl;
import com.myproject.service.impl.UserServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TaskTrackerBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(TaskTrackerBot.class);
    private final TaskService taskService = new TaskServiceImpl();
    private final UserService userService = new UserServiceImpl();

    private String botUsername;
    private String botToken;
    private final Map<Long, UserRole> userRoles = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> taskData = new ConcurrentHashMap<>();
    private final Map<Long, TaskCreationState> taskCreationState = new ConcurrentHashMap<>();
    private final Map<Long, TaskUpdateState> taskUpdateState = new ConcurrentHashMap<>();
    private final Map<Long, Long> taskIdToUpdate = new ConcurrentHashMap<>();
    private final Map<Long, TaskDetailsState> taskDetailsState = new ConcurrentHashMap<>();

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
            if (userRoles.containsKey(chatId) && !userService.getUserByChatId(chatId).isPresent()) {
                registerUser(chatId, messageText, userRoles.get(chatId));
                sendMessage(chatId, "Ви зареєстровані як " + userRoles.get(chatId).toString(), createCommandKeyboard(getUserRole(chatId)));
                userRoles.remove(chatId);
                return;
            }
            if (taskCreationState.containsKey(chatId)) {
                handleTaskCreationMessage(chatId, messageText);
                return;
            }
            if (taskUpdateState.containsKey(chatId)) {
                handleTaskUpdateMessage(chatId, messageText);
                return;
            }
            if (taskDetailsState.containsKey(chatId)) {
                handleTaskDetailsMessage(chatId, messageText);
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
                taskCreationState.put(chatId, TaskCreationState.SHOW_TEACHERS);
                showAllUsers(chatId);
                break;
            case SHOW_TEACHERS:
                showAllUsers(chatId);
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
    private void handleTaskUpdateMessage(Long chatId, String messageText) {
        TaskUpdateState state = taskUpdateState.get(chatId);
        switch (state) {
            case ID:
                try {
                    Long taskId = Long.parseLong(messageText);
                    taskIdToUpdate.put(chatId, taskId);
                    taskUpdateState.put(chatId, TaskUpdateState.STATUS);
                    sendMessage(chatId,"Оберіть новий статус завдання", createStatusKeyboard());
                }catch (NumberFormatException e) {
                    sendMessage(chatId,"Невірний ID завдання. Будь ласка, введіть коректний номер");
                }
                break;
        }

    }
    private void handleTaskDetailsMessage(Long chatId, String messageText) {
        TaskDetailsState state = taskDetailsState.get(chatId);
        if (state == TaskDetailsState.ID) {
            try {
                Long taskId = Long.parseLong(messageText);
                Optional<Task> taskOptional = taskService.getTaskById(taskId);
                if (taskOptional.isPresent()){
                    Task task = taskOptional.get();
                    sendMessage(chatId, String.format("<b>Завдання ID:</b> %d\n<b>Заголовок:</b> %s\n<b>Опис:</b> %s\n<b>Статус:</b> %s",
                            task.getId(), task.getTitle(), task.getDescription(), task.getStatus()));
                }else {
                    sendMessage(chatId, "Завдання з id " + taskId + " не знайдено");
                }
                taskDetailsState.remove(chatId);
            }
            catch (NumberFormatException e) {
                sendMessage(chatId,"Невірний ID завдання");
            }
        }
    }

    private void showAllUsers(Long chatId) {
        List<User> users = userService.getAllUsers();
        if (users.isEmpty()) {
            sendMessage(chatId, "Користувачів не знайдено");
            taskCreationState.put(chatId, TaskCreationState.TEACHER_ID);
            sendMessage(chatId, "Введіть ID викладача:");
        } else {
            String userList = users.stream()
                    .map(user -> String.format("<b>Користувач ID:</b> %d\n<b>Ім'я:</b> %s\n<b>Роль:</b> %s",
                            user.getId(), user.getName(), user.getRole()))
                    .collect(Collectors.joining("\n\n"));
            sendMessage(chatId, "Список користувачів:\n" + userList + "\nВведіть ID викладача:");
            taskCreationState.put(chatId, TaskCreationState.TEACHER_ID);
        }
    }

    private void createTaskFromData(Long chatId, Map<String, String> data) {
        try {
            String title = data.get("title");
            String description = data.get("description");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate deadlineDate = LocalDate.parse(data.get("deadline"), dateFormatter);
            Date deadline = java.sql.Date.valueOf(deadlineDate);
            if (deadlineDate.isBefore(LocalDate.now())) {
                sendMessage(chatId, "Дедлайн не може бути в минулому. Будь ласка, введіть коректну дату.");
                return;
            }
            System.out.println("ASdasdadasdasd");
            Long taskUserId = Long.parseLong(data.get("teacherId"));
            Optional<User> userOptional = userService.getUserById(taskUserId);
            if (userOptional.isPresent()){
                User user = userOptional.get();
                Task newTask = new Task(title, description, deadline, TaskStatus.PENDING, user);
                taskService.createTask(newTask);
                sendMessage(chatId, "Завдання створено з id: " + newTask.getId());
                sendNewTaskNotification(user.getChatId(), newTask);
            } else {
                sendMessage(chatId,"Користувача з id: " + taskUserId + " не знайдено");
            }
        } catch (DateTimeParseException e) {
            sendMessage(chatId, "Невірний формат дати. Будь ласка використовуйте рік-місяць-день");
        } catch (NumberFormatException e) {
            sendMessage(chatId,"Невірний ID користувача. Будь ласка, введіть коректний номер");
        }
    }
    private void sendNewTaskNotification(Long chatId, Task task) {
        String notificationText = String.format("<b>Нове завдання</b>\n<b>Заголовок:</b> %s\n<b>Дедлайн:</b> %s\n",
                task.getTitle(), new SimpleDateFormat("yyyy-MM-dd").format(task.getDeadline()));
        sendMessage(chatId, notificationText);
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
                handleUpdateTaskCommand(chatId, userId, userRole);
                break;
            case "/listUsers":
                if (userRole == UserRole.ADMIN) {
                    listUsers(chatId);
                }else {
                    sendMessage(chatId, "У вас немає доступу до цієї команди");
                }
                break;
            case "/taskDetails":
                handleTaskDetailsCommand(chatId, userId, userRole);
                break;
            default:
                sendMessage(chatId, "Невідома команда!");
                break;
        }
    }
    private void handleTaskDetailsCommand(Long chatId, Long userId, UserRole userRole) {
        if(userRole == UserRole.ADMIN) {
            List<Task> tasks = taskService.getAllTasks();
            if (tasks.isEmpty()){
                sendMessage(chatId, "Завдань не знайдено");
            } else {
                String taskList = tasks.stream()
                        .map(task -> String.format("<b>Завдання ID:</b> %d\n<b>Заголовок:</b> %s\n<b>Статус:</b> %s",
                                task.getId(), task.getTitle(), task.getStatus()))
                        .collect(Collectors.joining("\n\n"));
                sendMessage(chatId, "Список всіх завдань:\n" + taskList + "\nВведіть ID завдання для перегляду деталей:");
            }
        } else {
            Optional<User> userOptional =  userService.getUserById(userId);
            if(userOptional.isPresent()) {
                User user = userOptional.get();
                List<Task> tasks = taskService.getTasksByUser(user);
                if (tasks.isEmpty()) {
                    sendMessage(chatId, "Завдань для користувача " + user.getName() + " не знайдено");
                } else {
                    String taskList = tasks.stream()
                            .map(task -> String.format("<b>Завдання ID:</b> %d\n<b>Заголовок:</b> %s\n<b>Статус:</b> %s",
                                    task.getId(), task.getTitle(), task.getStatus()))
                            .collect(Collectors.joining("\n\n"));
                    sendMessage(chatId, "Ваш список завдань:\n" + taskList + "\nВведіть ID завдання для перегляду деталей:");
                }
            } else {
                sendMessage(chatId, "Користувача не знайдено");
            }
        }
        taskDetailsState.put(chatId, TaskDetailsState.ID);
    }
    private void handleUpdateTaskCommand(Long chatId, Long userId, UserRole userRole){
        if(userRole == UserRole.ADMIN) {
            List<Task> tasks = taskService.getAllTasks();
            if (tasks.isEmpty()){
                sendMessage(chatId, "Завдань не знайдено");
            } else {
                String taskList = tasks.stream()
                        .map(task -> String.format("<b>Завдання ID:</b> %d\n<b>Заголовок:</b> %s\n<b>Викладач:</b> %s\n<b>Дедлайн:</b> %s\n<b>Статус:</b> %s",
                                task.getId(), task.getTitle(),  task.getUser().getName(),
                                new SimpleDateFormat("yyyy-MM-dd").format(task.getDeadline()), task.getStatus()))
                        .collect(Collectors.joining("\n\n"));
                sendMessage(chatId, "Список всіх завдань:\n" + taskList + "\nВведіть ID завдання для зміни статусу:");
            }
        } else {
            Optional<User> userOptional =  userService.getUserById(userId);
            if(userOptional.isPresent()) {
                User user = userOptional.get();
                List<Task> tasks = taskService.getTasksByUser(user);
                if (tasks.isEmpty()) {
                    sendMessage(chatId, "Завдань для користувача " + user.getName() + " не знайдено");
                } else {
                    String taskList = tasks.stream()
                            .map(task -> String.format("<b>Завдання ID:</b> %d\n<b>Заголовок:</b> %s\n<b>Статус:</b> %s",
                                    task.getId(), task.getTitle(), task.getStatus()))
                            .collect(Collectors.joining("\n\n"));
                    sendMessage(chatId, "Ваш список завдань:\n" + taskList + "\nВведіть ID завдання для зміни статусу:");
                }
            } else {
                sendMessage(chatId, "Користувача не знайдено");
            }
        }
        taskUpdateState.put(chatId, TaskUpdateState.ID);
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
                    handleUpdateTaskCommand(chatId, userId, userRole);
                    break;
                case LIST_USERS:
                    handleListUsersButton(chatId,userRole);
                    break;
                case TASK_DETAILS:
                    handleTaskDetailsCommand(chatId, userId, userRole);
                    break;
                case HELP:
                    sendHelpMessage(chatId, userRole);
                    break;
                case PENDING:
                case IN_PROGRESS:
                case COMPLETED:
                case OVERDUE:
                    handleUpdateTaskStatus(chatId, callback);
                    break;
            }
        }catch (IllegalArgumentException e) {
            sendMessage(chatId, "Невідома команда!");
        }
    }
    private void handleUpdateTaskStatus(Long chatId, CallbackData callback) {
        Long taskId = taskIdToUpdate.get(chatId);
        Optional<Task> optionalTask = taskService.getTaskById(taskId);
        if (optionalTask.isPresent()) {
            Task task = optionalTask.get();
            task.setStatus(TaskStatus.valueOf(callback.getCallbackData()));
            taskService.updateTask(task);
            sendMessage(chatId,"Статус завдання " + taskId + " оновлено на " + callback.getCallbackData());
        } else {
            sendMessage(chatId,"Завдання з id: " + taskId + " не знайдено");
        }
        taskUpdateState.remove(chatId);
        taskIdToUpdate.remove(chatId);
    }
    private void handleRoleSelection(Long chatId, CallbackData callback) {
        sendMessage(chatId,"Будь ласка, введіть своє ім'я:");
        userRoles.put(chatId, callback == CallbackData.ADMIN ? UserRole.ADMIN : UserRole.TEACHER);
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

    private InlineKeyboardMarkup createStatusKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        for (TaskStatus status : TaskStatus.values()) {
            InlineKeyboardButton button = new InlineKeyboardButton(status.toString());
            button.setCallbackData(status.toString());
            row1.add(button);
        }
        keyboard.add(row1);
        markup.setKeyboard(keyboard);
        return markup;
    }

    private Long getUserIdFromChatId(Long chatId) {
        Optional<User> user = userService.getUserByChatId(chatId);
        user.ifPresent(userObj -> logger.info("User id found from chatId {} is: {}", chatId, userObj.getId()));
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
                    + "/updateTask - Оновити статус завдання\n"
                    + "/listUsers - Переглянути всіх користувачів\n"
                    + "/taskDetails - Переглянути деталі завдання\n"
                    + "/help - Показати це повідомлення";
        }else {
            helpText = "Доступні команди:\n"
                    + "/start - Розпочати роботу з ботом\n"
                    + "/myTasks - Переглянути мої завдання\n"
                    + "/updateTask - Оновити статус завдання\n"
                    + "/taskDetails - Переглянути деталі завдання\n"
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
    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }
    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.enableHtml(true);
        if (markup != null) {
            message.setReplyMarkup(markup);
        }
        try {
            this.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
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
    private void listUsers(Long chatId) {
        List<User> users = userService.getAllUsers();
        if(users.isEmpty()) {
            sendMessage(chatId, "Користувачів не знайдено");
        } else {
            String userList = users.stream()
                    .map(user -> String.format("<b>Користувач ID:</b> %d\n<b>Ім'я:</b> %s\n<b>Роль:</b> %s",
                            user.getId(), user.getName(), user.getRole()))
                    .collect(Collectors.joining("\n\n"));
            sendMessage(chatId, userList);
        }
    }
    private void listTasks(Long chatId) {
        List<Task> tasks = taskService.getAllTasks();
        if (tasks.isEmpty()){
            sendMessage(chatId, "Завдань не знайдено");
        } else {
            String taskList = tasks.stream()
                    .map(task -> String.format("<b>Завдання ID:</b> %d\n<b>Заголовок:</b> %s\n<b>Викладач:</b> %s\n<b>Дедлайн:</b> %s\n<b>Статус:</b> %s",
                            task.getId(), task.getTitle(),  task.getUser().getName(),
                            new SimpleDateFormat("yyyy-MM-dd").format(task.getDeadline()), task.getStatus()))
                    .collect(Collectors.joining("\n\n"));
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
                            .map(task -> String.format("<b>Завдання ID:</b> %d\n<b>Заголовок:</b> %s\n<b>Дедлайн:</b> %s\n<b>Статус:</b> %s",
                                    task.getId(), task.getTitle(), new SimpleDateFormat("yyyy-MM-dd").format(task.getDeadline()), task.getStatus()))
                            .collect(Collectors.joining("\n\n"));
                    sendMessage(chatId, taskList);
                }
            } else {
                sendMessage(chatId, "Користувача не знайдено");
            }
        } else {
            sendMessage(chatId, "Користувача не знайдено");
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
        HELP("help"),
        PENDING("PENDING"),
        IN_PROGRESS("IN_PROGRESS"),
        COMPLETED("COMPLETED"),
        OVERDUE("OVERDUE");


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
        SHOW_TEACHERS,
        TEACHER_ID
    }
    private enum TaskUpdateState {
        ID,
        STATUS
    }
    private enum TaskDetailsState {
        ID
    }
}