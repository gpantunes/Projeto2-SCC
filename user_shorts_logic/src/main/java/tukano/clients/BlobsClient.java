package tukano.clients;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.io.IOException;

import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;

import org.jvnet.hk2.internal.ErrorResults;
import tukano.api.Result;

import static tukano.api.Result.error;

public class BlobsClient {
    private final String baseUrl;
    private final HttpClient httpClient;

    public BlobsClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.httpClient = HttpClient.newHttpClient();
    }

    public Result setCookie(String cookie, String userId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "blobs/cookie?token=" + cookie + "&userId=" + userId))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 220) {
            System.out.println("A response deu 200");
            return Result.ok();
        } else {
            return Result.error(errorCodeFromStatus(response.statusCode()));
        }
    }

    public byte[] downloadBlob(String blobName) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "blobs/download/" + blobName))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new IOException("Failed to download blob: " + response.statusCode() + " " + response.body());
        }
    }

    public Result deleteBlob(String BLOB_ID, String token) throws IOException, InterruptedException {
        System.out.println("Entrou no delete do blobs client");
        String uri = String.format("%sblobs/%s?token=%s", baseUrl, BLOB_ID, token);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("A resposta foi " + response.statusCode());

        if (response.statusCode() >= 200 && response.statusCode() < 220) {
            System.out.println("A response deu 200");
            return Result.ok(response.body());
        } else {
            return Result.error(errorCodeFromStatus(response.statusCode()));
        }
    }



    public Result<Void> deleteAllBlobs(String USER_ID, String token) throws Exception {
        System.out.println("Entrou no delete all blobs do blobs client");
        String uri = String.format("%sblobs/%s/blobs?token=%s", baseUrl, USER_ID, token);
        System.out.println("blobs-service uri " + uri);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("A resposta foi " + response.statusCode());

        if (response.statusCode() >= 200 && response.statusCode() < 220) {
            System.out.println("A response deu 200");
            return Result.ok();
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
