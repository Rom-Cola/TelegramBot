package com.myproject.repository;

import com.myproject.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User createUser(User user);
    Optional<User> getUserById(Long id);
    List<User> getAllUsers();
    void deleteUser(Long id);
    User updateUser(User user);
    Optional<User> getUserByChatId(Long chatId);
}