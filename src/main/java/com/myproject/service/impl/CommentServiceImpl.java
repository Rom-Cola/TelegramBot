package com.myproject.service.impl;

import com.myproject.model.Comment;
import com.myproject.repository.CommentRepository;
import com.myproject.repository.impl.CommentRepositoryImpl;
import com.myproject.service.CommentService;

import java.util.List;
import java.util.Optional;

public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository = new CommentRepositoryImpl();

    @Override
    public Comment createComment(Comment comment) {
        return commentRepository.createComment(comment);
    }

    @Override
    public Optional<Comment> getCommentById(Long id) {
        return commentRepository.getCommentById(id);
    }

    @Override
    public List<Comment> getAllComments() {
        return commentRepository.getAllComments();
    }

    @Override
    public void deleteComment(Long id) {
        commentRepository.deleteComment(id);
    }
    @Override
    public List<Comment> getCommentsByTaskId(Long taskId) {
        return commentRepository.getCommentsByTaskId(taskId);
    }
}