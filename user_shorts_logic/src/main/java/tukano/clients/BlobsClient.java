import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;

public class BlobsClient {
    private final String baseUrl;
    private final HttpClient httpClient;

    public BlobsClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.httpClient = HttpClient.newHttpClient();
    }

    public String uploadBlob(String blobName, Path filePath) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "blobs/upload/" + blobName))
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(filePath))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body(); // Could be a blob ID or success message
        } else {
            throw new IOException("Failed to upload blob: " + response.statusCode() + " " + response.body());
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

    public String deleteBlob(String BLOB_ID) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/{" + BLOB_ID + "}"))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body(); // Could be a confirmation message
        } else {
            throw new IOException("Failed to delete blob: " + response.statusCode() + " " + response.body());
        }
    }

    public String listBlobs() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "blobs/list"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body(); // Could be a JSON or plain text list of blob names
        } else {
            throw new IOException("Failed to list blobs: " + response.statusCode() + " " + response.body());
        }
    }
}
