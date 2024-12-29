package com.myproject.service;

import com.myproject.model.Comment;

import java.util.List;
import java.util.Optional;


public interface CommentService {
    Comment createComment(Comment comment);
    Optional<Comment> getCommentById(Long id);
    List<Comment> getAllComments();
    void deleteComment(Long id);
    List<Comment> getCommentsByTaskId(Long taskId);
}