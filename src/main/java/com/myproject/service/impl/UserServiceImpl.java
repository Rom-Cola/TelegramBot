package com.myproject.service.impl;


import com.myproject.model.User;
import com.myproject.repository.UserRepository;
import com.myproject.repository.impl.UserRepositoryImpl;
import com.myproject.service.UserService;

import java.util.List;
import java.util.Optional;


public class UserServiceImpl implements UserService {

    private final UserRepository userRepository = new UserRepositoryImpl();


    @Override
    public User createUser(User user) {
        return userRepository.createUser(user);
    }

    @Override
    public Optional<User> getUserById(Long id) {
        return userRepository.getUserById(id);
    }
    @Override
    public Optional<User> getUserByChatId(Long chatId) {
        return userRepository.getUserByChatId(chatId);
    }
    @Override
    public List<User> getAllUsers() {
        return userRepository.getAllUsers();
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteUser(id);
    }

    @Override
    public User updateUser(User user) {
        return userRepository.updateUser(user);
    }
}