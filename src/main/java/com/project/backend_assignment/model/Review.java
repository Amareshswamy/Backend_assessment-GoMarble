package com.project.backend_assignment.model;

import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;

@Data
@Builder
public class Review {
    private String title;
    private String body;
    private int rating;
    private String reviewer;

}