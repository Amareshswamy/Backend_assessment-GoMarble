package com.project.backend_assignment.controller;

import com.project.backend_assignment.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }


    @GetMapping("/reviews")
    public Map<String, Object> getReviews(@RequestParam String page) throws IOException, InterruptedException {
        return reviewService.getRevieweData(page);
    }
}
