package dev.dynamiq.talli.service.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GithubRepositoryClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final GithubAppTokenService tokenService;
    private final String apiVersion;
    private final String committerName;
    private final String committerEmail;

    public GithubRepositoryClient(ObjectMapper objectMapper,
                                  GithubAppTokenService tokenService,
                                  @Value("${app.github.api-version:2022-11-28}") String apiVersion,
                                  @Value("${app.github.committer-name:Talli}") String committerName,
                                  @Value("${app.github.committer-email:talli@dynamiq.dev}") String committerEmail) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.tokenService = tokenService;
        this.apiVersion = apiVersion;
        this.committerName = committerName;
        this.committerEmail = committerEmail;
    }

    public Long findInstallationId(String owner, String repo) {
        JsonNode json = request("GET", "/repos/" + enc(owner) + "/" + enc(repo) + "/installation",
                tokenService.appJwt(), null);
        return json.path("id").asLong();
    }

    public byte[] readFile(String owner, String repo, String branch, Long installationId, String path) {
        String token = tokenService.installationToken(installationId);
        JsonNode json = request("GET", "/repos/" + enc(owner) + "/" + enc(repo)
                        + "/contents/" + encodePath(path) + "?ref=" + enc(branch),
                token, null);

        String encoding = json.path("encoding").asText();
        if (!"base64".equals(encoding)) {
            throw new GithubApiException(422, "Unsupported GitHub content encoding: " + encoding);
        }

        String content = json.path("content").asText().replace("\n", "");
        return Base64.getDecoder().decode(content);
    }

    public String branchHeadSha(String owner, String repo, String branch, Long installationId) {
        String token = tokenService.installationToken(installationId);
        JsonNode json = request("GET", "/repos/" + enc(owner) + "/" + enc(repo)
                        + "/git/ref/heads/" + enc(branch),
                token, null);
        return json.path("object").path("sha").asText();
    }

    public GithubCommitResult commitFiles(String owner,
                                          String repo,
                                          String branch,
                                          Long installationId,
                                          String message,
                                          List<GithubFileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new IllegalArgumentException("No file changes to commit.");
        }

        String token = tokenService.installationToken(installationId);
        String parentSha = branchHeadSha(owner, repo, branch, installationId);
        String baseTreeSha = commitTreeSha(owner, repo, parentSha, token);

        List<Map<String, Object>> treeEntries = new ArrayList<>();
        for (GithubFileChange change : changes) {
            String blobSha = createBlob(owner, repo, token, change.content());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", change.path());
            entry.put("mode", "100644");
            entry.put("type", "blob");
            entry.put("sha", blobSha);
            treeEntries.add(entry);
        }

        String treeSha = createTree(owner, repo, token, baseTreeSha, treeEntries);
        String commitSha = createCommit(owner, repo, token, message, treeSha, parentSha);
        updateRef(owner, repo, branch, token, commitSha);

        return new GithubCommitResult(commitSha);
    }

    private String commitTreeSha(String owner, String repo, String commitSha, String token) {
        JsonNode json = request("GET", "/repos/" + enc(owner) + "/" + enc(repo)
                        + "/git/commits/" + enc(commitSha),
                token, null);
        return json.path("tree").path("sha").asText();
    }

    private String createBlob(String owner, String repo, String token, byte[] bytes) {
        Map<String, Object> body = Map.of(
                "content", Base64.getEncoder().encodeToString(bytes),
                "encoding", "base64"
        );
        JsonNode json = request("POST", "/repos/" + enc(owner) + "/" + enc(repo) + "/git/blobs",
                token, body);
        return json.path("sha").asText();
    }

    private String createTree(String owner, String repo, String token, String baseTreeSha,
                              List<Map<String, Object>> treeEntries) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("base_tree", baseTreeSha);
        body.put("tree", treeEntries);

        JsonNode json = request("POST", "/repos/" + enc(owner) + "/" + enc(repo) + "/git/trees",
                token, body);
        return json.path("sha").asText();
    }

    private String createCommit(String owner, String repo, String token, String message,
                                String treeSha, String parentSha) {
        Map<String, Object> committer = new LinkedHashMap<>();
        committer.put("name", committerName);
        committer.put("email", committerEmail);
        committer.put("date", OffsetDateTime.now().toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("tree", treeSha);
        body.put("parents", List.of(parentSha));
        body.put("committer", committer);
        body.put("author", committer);

        JsonNode json = request("POST", "/repos/" + enc(owner) + "/" + enc(repo) + "/git/commits",
                token, body);
        return json.path("sha").asText();
    }

    private void updateRef(String owner, String repo, String branch, String token, String commitSha) {
        Map<String, Object> body = Map.of("sha", commitSha, "force", false);
        request("PATCH", "/repos/" + enc(owner) + "/" + enc(repo) + "/git/refs/heads/" + enc(branch),
                token, body);
    }

    private JsonNode request(String method, String path, String bearerToken, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com" + path))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + bearerToken)
                    .header("X-GitHub-Api-Version", apiVersion)
                    .header("User-Agent", "Talli");

            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GithubApiException(response.statusCode(), errorMessage(response.body()));
            }

            return response.body() == null || response.body().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response.body());
        } catch (GithubApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("GitHub request failed: " + e.getMessage(), e);
        }
    }

    private String errorMessage(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            if (json.hasNonNull("message")) {
                return json.get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return body;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodePath(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .map(GithubRepositoryClient::enc)
                .collect(Collectors.joining("/"));
    }
}
