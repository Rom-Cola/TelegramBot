package com.myproject.service;

import com.myproject.model.Task;
import com.myproject.model.User;

import java.util.List;
import java.util.Optional;

public interface TaskService {

    Task createTask(Task task);
    Optional<Task> getTaskById(Long id);
    List<Task> getAllTasks();
    List<Task> getTasksByUser(User user);
    void deleteTask(Long id);
    Task updateTask(Task task);

}