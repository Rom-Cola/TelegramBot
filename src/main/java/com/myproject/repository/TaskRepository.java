package com.myproject.repository;

import com.myproject.model.Task;
import com.myproject.model.User;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    Task createTask(Task task);
    Optional<Task> getTaskById(Long id);
    List<Task> getAllTasks();
    void deleteTask(Long id);
    Task updateTask(Task task);
    List<Task> getTasksByUser(User user);
}