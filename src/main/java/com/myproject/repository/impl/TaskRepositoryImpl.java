package com.myproject.repository.impl;

import com.myproject.model.Task;
import com.myproject.model.User;
import com.myproject.repository.TaskRepository;
import com.myproject.utils.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.List;
import java.util.Optional;

public class TaskRepositoryImpl implements TaskRepository {
    private static final Logger logger = LoggerFactory.getLogger(TaskRepositoryImpl.class);
    @Override
    public Task createTask(Task task) {
        Transaction transaction = null;
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            transaction = session.beginTransaction();
            session.persist(task);
            transaction.commit();
            return task;
        }catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            logger.error("Error when creating task" , e);
            throw new RuntimeException("Error when creating task", e);
        }
    }

    @Override
    public Optional<Task> getTaskById(Long id) {
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            return Optional.ofNullable(session.find(Task.class, id));
        }catch (Exception e) {
            logger.error("Error when getting task by id" , e);
            throw new RuntimeException("Error when getting task by id", e);
        }
    }

    @Override
    public List<Task> getAllTasks() {
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            return session.createQuery("from Task", Task.class).list();
        }catch (Exception e) {
            logger.error("Error when getting all tasks" , e);
            throw new RuntimeException("Error when getting all tasks", e);
        }
    }

    @Override
    public void deleteTask(Long id) {
        Transaction transaction = null;
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            transaction = session.beginTransaction();
            Task task = session.find(Task.class, id);
            if (task != null){
                session.remove(task);
            }
            transaction.commit();
        }catch (Exception e) {
            if (transaction != null){
                transaction.rollback();
            }
            logger.error("Error when deleting task by id" , e);
            throw new RuntimeException("Error when deleting task by id", e);
        }
    }

    @Override
    public Task updateTask(Task task) {
        Transaction transaction = null;
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            transaction = session.beginTransaction();
            session.merge(task);
            transaction.commit();
            return task;
        }catch (Exception e) {
            if (transaction != null){
                transaction.rollback();
            }
            logger.error("Error when updating task" , e);
            throw new RuntimeException("Error when updating task", e);
        }
    }

    @Override
    public List<Task> getTasksByUser(User user) {
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            return session.createQuery("from Task t where t.user = :user", Task.class)
                    .setParameter("user", user).list();

        }catch (Exception e) {
            logger.error("Error when getting tasks by user" , e);
            throw new RuntimeException("Error when getting tasks by user", e);
        }
    }
}