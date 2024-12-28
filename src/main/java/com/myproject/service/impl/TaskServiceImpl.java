package com.myproject.service.impl;

import com.myproject.model.Task;
import com.myproject.model.User;
import com.myproject.repository.TaskRepository;
import com.myproject.repository.impl.TaskRepositoryImpl;
import com.myproject.service.TaskService;


import java.util.List;
import java.util.Optional;

public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository = new TaskRepositoryImpl();

    @Override
    public Task createTask(Task task) {
        return taskRepository.createTask(task);
    }

    @Override
    public Optional<Task> getTaskById(Long id) {
        return taskRepository.getTaskById(id);
    }

    @Override
    public List<Task> getAllTasks() {
        return taskRepository.getAllTasks();
    }

    @Override
    public List<Task> getTasksByUser(User user) {
        return taskRepository.getTasksByUser(user);
    }

    @Override
    public void deleteTask(Long id) {
        taskRepository.deleteTask(id);
    }

    @Override
    public Task updateTask(Task task) {
        return taskRepository.updateTask(task);
    }
}