package io.dockstore.jira;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public final class Utils {

    public static final String JIRA_REST_BASE_URL = "https://ucsc-cgl.atlassian.net/rest/api/3/";
    private static final Pattern JIRA_ISSUE_IN_GITHUB_BODY = Pattern.compile("((Issue Number)|(friendlyId)): (DOCK-\\d+)");
    private static final int JIRA_ISSUE_GROUP = 4;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String JIRA_USERNAME = "JIRA_USERNAME";
    private static final String JIRA_TOKEN = "JIRA_TOKEN";

    private Utils() { }

    public static JiraIssue getJiraIssue(String issueId)
        throws URISyntaxException, IOException, InterruptedException {
        final URI uri = new URI(JIRA_REST_BASE_URL + "issue/" + issueId);
        final HttpRequest httpRequest = authorizedRequestBuilder()
            .GET()
            .uri(uri)
            .build();
        final HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, BodyHandlers.ofString());
        final Gson gson = new Gson();
        final String body = response.body();
        System.out.println("body = " + body);
        final JiraIssue jiraIssue = gson.fromJson(body, JiraIssue.class);
        System.out.println("jiraIssue = " + jiraIssue);
        return jiraIssue;
    }


    public static List<FixVersion> getJiraFixVersions()
        throws URISyntaxException, IOException, InterruptedException {
        final URI uri = new URI(JIRA_REST_BASE_URL + "project/DOCK/versions");
        final HttpRequest httpRequest = authorizedRequestBuilder().GET().uri(uri).build();
        final HttpResponse<String> response =
            HTTP_CLIENT.send(httpRequest, BodyHandlers.ofString());
        final Gson gson = new Gson();
        final FixVersion[] fixVersions = gson.fromJson(response.body(), FixVersion[].class);
        System.out.println("fixVersions = " + fixVersions);
        return List.of(fixVersions);
    }

    public static GHRepository getDockstoreRepository() throws IOException {
        final GitHub gitHub =
            new GitHubBuilder().withAuthorizationProvider(
                () -> "Bearer " + System.getenv("GITHUB_TOKEN")).build();
        return gitHub.getRepository("dockstore/dockstore");
    }

    /**
     * API to get issues returns both issues and pull requests; filter out pull requests
     * @param repository
     * @return
     * @throws IOException
     */
    public static List<GHIssue> findOpenIssues(GHRepository repository) throws IOException {
        return repository.getIssues(GHIssueState.OPEN).stream()
            .filter(ghIssue -> !ghIssue.isPullRequest())
            .toList();
    }

    public static Optional<String> findJiraIssueInBody(GHIssue ghIssue) {
        final Matcher matcher = JIRA_ISSUE_IN_GITHUB_BODY.matcher(ghIssue.getBody());
        if (matcher.find()) {
            return Optional.of(matcher.group(JIRA_ISSUE_GROUP));
        }
        return Optional.empty();
    }

    private static Builder authorizedRequestBuilder() {
        return HttpRequest.newBuilder().header("Authorization", getAuthHeaderValue());
    }

    private static String getAuthHeaderValue() {
        final String username = System.getenv(JIRA_USERNAME);
        final String jiraToken = System.getenv(JIRA_TOKEN);
        return "Basic %s".formatted(
                Base64.getEncoder().encodeToString((username + ':' + jiraToken).getBytes()));
    }


}
