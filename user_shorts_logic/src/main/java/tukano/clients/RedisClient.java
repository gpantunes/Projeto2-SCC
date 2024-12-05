package tukano.clients;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;

public class RedisClient {
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RedisClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public String get(String key) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "get/" + key))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Failed to get key: " + response.statusCode() + " " + response.body());
        }
    }

    public String set(String key, String value) throws Exception {
        HashMap<String, String> data = new HashMap<>();
        data.put("key", key);
        data.put("value", value);

        String requestBody = objectMapper.writeValueAsString(data);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "set"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Failed to set key: " + response.statusCode() + " " + response.body());
        }
    }

    public String delete(String key) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "delete/" + key))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Failed to delete key: " + response.statusCode() + " " + response.body());
        }
    }

    public static void main(String[] args) {
        try {
            RedisClient redisClient = new RedisClient("http://redis-rest-service:8080/");

            // Set a value
            String setResponse = redisClient.set("exampleKey", "exampleValue");
            System.out.println("Set response: " + setResponse);

            // Get the value
            String value = redisClient.get("exampleKey");
            System.out.println("Get response: " + value);

            // Delete the key
            String deleteResponse = redisClient.delete("exampleKey");
            System.out.println("Delete response: " + deleteResponse);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
