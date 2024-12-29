package com.myproject.repository.impl;

import com.myproject.model.User;
import com.myproject.repository.UserRepository;
import com.myproject.utils.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class UserRepositoryImpl implements UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserRepositoryImpl.class);
    @Override
    public User createUser(User user) {
        Transaction transaction = null;
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            transaction = session.beginTransaction();
            session.persist(user);
            transaction.commit();
            return user;
        }catch (Exception e) {
            if(transaction != null) {
                transaction.rollback();
            }
            logger.error("Error when creating user" , e);
            throw new RuntimeException("Error when creating user", e);
        }

    }

    @Override
    public Optional<User> getUserById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            User user = session.get(User.class, id);
            return Optional.ofNullable(user);
        } catch (Exception e) {
            logger.error("Error when getting user by id" , e);
            throw new RuntimeException("Error when getting user by id", e);
        }
    }
    @Override
    public Optional<User> getUserByChatId(Long chatId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Optional<User> user = session.createQuery("from User u where u.chatId = :chatId", User.class)
                    .setParameter("chatId", chatId)
                    .uniqueResultOptional();
            return user;
        } catch (Exception e) {
            logger.error("Error when getting user by chatId", e);
            throw new RuntimeException("Error when getting user by chatId", e);
        }
    }


    @Override
    public List<User> getAllUsers() {
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from User", User.class).list();
        }catch (Exception e) {
            logger.error("Error when getting all users" , e);
            throw new RuntimeException("Error when getting all users", e);
        }
    }

    @Override
    public void deleteUser(Long id) {
        Transaction transaction = null;
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            transaction = session.beginTransaction();
            User user = session.find(User.class, id);
            if (user != null){
                session.remove(user);
            }
            transaction.commit();
        }catch (Exception e) {
            if (transaction != null){
                transaction.rollback();
            }
            logger.error("Error when deleting user by id" , e);
            throw new RuntimeException("Error when deleting user by id", e);
        }

    }

    @Override
    public User updateUser(User user) {
        Transaction transaction = null;
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            transaction = session.beginTransaction();
            User userToUpdate = session.find(User.class, user.getId());
            if (userToUpdate != null) {
                userToUpdate.setName(user.getName());
                userToUpdate.setRole(user.getRole());
                session.merge(userToUpdate);
            }
            transaction.commit();
            return userToUpdate;
        }catch (Exception e) {
            if(transaction != null) {
                transaction.rollback();
            }
            logger.error("Error when updating user" , e);
            throw new RuntimeException("Error when updating user", e);
        }
    }
}