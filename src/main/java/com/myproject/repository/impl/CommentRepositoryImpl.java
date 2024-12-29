package com.myproject.repository.impl;

import com.myproject.model.Comment;
import com.myproject.repository.CommentRepository;
import com.myproject.utils.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class CommentRepositoryImpl implements CommentRepository {

    private static final Logger logger = LoggerFactory.getLogger(CommentRepositoryImpl.class);
    @Override
    public Comment createComment(Comment comment) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(comment);
            transaction.commit();
            return comment;
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            logger.error("Error when creating comment", e);
            throw new RuntimeException("Error when creating comment", e);
        }
    }


    @Override
    public Optional<Comment> getCommentById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return Optional.ofNullable(session.find(Comment.class, id));
        } catch (Exception e) {
            logger.error("Error when getting comment by id" , e);
            throw new RuntimeException("Error when getting comment by id", e);
        }
    }

    @Override
    public List<Comment> getAllComments() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from Comment", Comment.class).list();
        } catch (Exception e) {
            logger.error("Error when getting all comments", e);
            throw new RuntimeException("Error when getting all comments", e);
        }
    }


    @Override
    public void deleteComment(Long id) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            Comment comment = session.find(Comment.class, id);
            if (comment != null){
                session.remove(comment);
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            logger.error("Error when deleting comment by id", e);
            throw new RuntimeException("Error when deleting comment by id", e);
        }
    }
    @Override
    public List<Comment> getCommentsByTaskId(Long taskId) {
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            return session.createQuery("from Comment c where c.taskId = :taskId", Comment.class)
                    .setParameter("taskId", taskId).list();
        }catch (Exception e) {
            logger.error("Error when getting comments by taskId" , e);
            throw new RuntimeException("Error when getting comments by taskId", e);
        }
    }
}