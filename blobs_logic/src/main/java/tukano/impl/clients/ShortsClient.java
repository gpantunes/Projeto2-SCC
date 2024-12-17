package tukano.impl.clients;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;

import org.jvnet.hk2.internal.ErrorResults;
import tukano.api.Result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ShortsClient {
    private final String baseUrl;
    private final HttpClient httpClient;

    public ShortsClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.httpClient = HttpClient.newHttpClient();
    }



    public Result<List<String>> getShorts(String USER_ID) throws IOException, InterruptedException {
        System.out.println("Entrou no get shorts do client");
        String uri = String.format("%sshorts/%s/shorts", baseUrl, USER_ID);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("A resposta foi " + response.statusCode());

        if (response.statusCode() >= 200 && response.statusCode() < 220) {
            System.out.println("A response deu 200");
            ObjectMapper objectMapper = new ObjectMapper();
            List<String> strings = objectMapper.readValue(response.body(), new TypeReference<List<String>>() {});
            return Result.ok(strings);
        } else {
            return Result.error(errorCodeFromStatus(response.statusCode()));
        }
    }


    static Result.ErrorCode errorCodeFromStatus(int status) {
        return switch (status) {
            case 200 ->
                    Result.ErrorCode.OK;
            case 404 ->
                    Result.ErrorCode.NOT_FOUND;
            case 409 ->
                    Result.ErrorCode.CONFLICT;
            default ->
                    Result.ErrorCode.INTERNAL_ERROR;
        };
    }
}
