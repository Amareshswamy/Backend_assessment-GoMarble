package com.project.backend_assignment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.project.backend_assignment.model.Review;
import lombok.Builder;
import okhttp3.*;
import org.springframework.stereotype.Service;
import com.microsoft.playwright.options.LoadState;
import com.project.backend_assignment.model.Review;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import lombok.Builder;
import okhttp3.*;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.io.IOException;
import java.util.*;

@Service
@Builder
public class ReviewService {

    private static final String OPENAI_API_KEY = ""; // Key cannot be pushed due to github violations
    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_RETRIES = 5;

    public Map<String, Object> getRevieweData(String pageUrl) throws IOException, InterruptedException {
        // Initialize Playwright resources
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext();
             Page webPage = context.newPage()) {

            // Navigate to the provided page URL
            webPage.navigate(pageUrl);

            // Fetch the review CSS selector using LLM
            String reviewSelector = getReviewSelectorFromLLM(pageUrl);

            // Scrape reviews using the dynamically identified CSS selector
            List<Review> reviews = new ArrayList<>();
            int reviewsCount = scrapeReviewsWithPagination(webPage, reviewSelector, reviews);

            // Return the reviews in the required format
            return createResponse(reviewsCount, reviews);
        }
    }

    // Method to get the CSS selector for reviews using OpenAI
    private String getReviewSelectorFromLLM(String pageUrl) throws IOException, InterruptedException {
        String prompt = "Identify the CSS selector for product reviews on this page: " + pageUrl;

        Map<String, Object> json = createOpenAIRequestPayload(prompt);

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(json),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/completions")
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .post(body)
                .build();

        return executeOpenAIRequestWithRetries(request);
    }

    // Helper method to create request payload for OpenAI
    private Map<String, Object> createOpenAIRequestPayload(String prompt) {
        Map<String, Object> json = new HashMap<>();
        json.put("model", "gpt-3.5-turbo");
        json.put("prompt", prompt);
        json.put("max_tokens", 50);
        return json;
    }

    // Helper method to handle OpenAI API requests with retries
    private String executeOpenAIRequestWithRetries(Request request) throws IOException, InterruptedException {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 429) {
                    handleRateLimit(response);
                    attempts++;
                    continue; // Retry the request
                } else if (response.isSuccessful()) {
                    JsonNode jsonResponse = objectMapper.readTree(response.body().string());
                    return jsonResponse.get("choices").get(0).get("text").asText().trim();
                } else {
                    throw new IOException("Unexpected code " + response);
                }
            }
        }
        throw new IOException("Exceeded maximum retries for OpenAI API request.");
    }

    // Helper method to handle rate-limiting headers and retry delays
    private void handleRateLimit(Response response) throws InterruptedException {
        System.out.println("Rate limit headers:");
        System.out.println("X-RateLimit-Limit: " + response.header("X-RateLimit-Limit"));
        System.out.println("X-RateLimit-Remaining: " + response.header("X-RateLimit-Remaining"));
        System.out.println("X-RateLimit-Reset: " + response.header("X-RateLimit-Reset"));

        Thread.sleep(5000); // Delay before retrying
    }

    // Method to scrape reviews and handle pagination
    private int scrapeReviewsWithPagination(Page webPage, String reviewSelector, List<Review> reviews) {
        int reviewsCount = 0;
        boolean hasNextPage = true;

        while (hasNextPage) {
            List<Review> pageReviews = scrapeReviews(webPage, reviewSelector);
            reviews.addAll(pageReviews);
            reviewsCount += pageReviews.size();

            // Check if there's a next page (handle pagination)
            hasNextPage = webPage.querySelector("button.next") != null;
            if (hasNextPage) {
                webPage.click("button.next");
                webPage.waitForLoadState(LoadState.LOAD);
            }
        }
        return reviewsCount;
    }

    // Method to scrape reviews from the web page using the provided CSS selector
    private List<Review> scrapeReviews(Page webPage, String reviewSelector) {
        List<Review> reviews = new ArrayList<>();
        List<ElementHandle> reviewElements = webPage.querySelectorAll(reviewSelector);

        for (ElementHandle reviewElement : reviewElements) {
            Review review = extractReviewDetails(reviewElement);
            reviews.add(review);
        }

        return reviews;
    }

    // Helper method to extract review details from a review element
    private Review extractReviewDetails(ElementHandle reviewElement) {
        String title = getElementText(reviewElement, ".review-title");
        String body = getElementText(reviewElement, ".review-body");
        String rating = getElementText(reviewElement, ".review-rating");
        String reviewer = getElementText(reviewElement, ".review-author");

        return Review.builder()
                .title(title)
                .body(body)
                .rating(Integer.parseInt(rating))
                .reviewer(reviewer)
                .build();
    }

    // Helper method to safely extract text from an element
    private String getElementText(ElementHandle element, String selector) {
        ElementHandle child = element.querySelector(selector);
        return (child != null) ? child.innerText() : "";
    }

    // Helper method to create the final response map
    private Map<String, Object> createResponse(int reviewsCount, List<Review> reviews) {
        Map<String, Object> response = new HashMap<>();
        response.put("reviews_count", reviewsCount);
        response.put("reviews", reviews);
        return response;
    }
}
