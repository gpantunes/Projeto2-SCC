package tukano.impl.clients;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ShortsClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Constructor
    public ShortsClient(String serviceName) {
        this.baseUrl = "http://" + serviceName; // Base URL for the user-shorts-service
        this.httpClient = HttpClient.newHttpClient(); // Create the HTTP client
        this.objectMapper = new ObjectMapper(); // For JSON serialization/deserialization
    }


    // Search for users
    public List<String> getShorts(String query) throws Exception {
        String endpoint = baseUrl + "/rest/shorts/?query=" + query;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } else {
            throw new RuntimeException("Failed to search users: " + response.body());
        }
    }
}
