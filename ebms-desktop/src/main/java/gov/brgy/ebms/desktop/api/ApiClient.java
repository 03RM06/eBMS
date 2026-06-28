package gov.brgy.ebms.desktop.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ApiClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private String accessToken;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    }

    public ApiClient() {
        this(DEFAULT_BASE_URL);
    }

    public LoginResult login(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
            new LoginPayload(username, password)
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(TIMEOUT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            LoginResult result = objectMapper.readValue(response.body(), LoginResult.class);
            this.accessToken = result.accessToken();
            return result;
        }
        throw new ApiException("Login failed: " + response.statusCode() + " " + response.body());
    }

    public String getAccessToken() {
        return accessToken;
    }

    public record LoginPayload(String username, String password) {}

    public record LoginResult(
        String accessToken,
        String refreshToken,
        Long userId,
        String username,
        String fullName,
        java.util.List<String> roles
    ) {}

    public static class ApiException extends RuntimeException {
        public ApiException(String message) {
            super(message);
        }
    }
}
