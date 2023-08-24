package io.dockstore.jira;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public final class SprintStart {

    private static final String PROJECT = "SEAB";
    private static final String BASE_URL = "https://ucsc-cgl.atlassian.net/rest/api/3/";
    private static final String USERS_URL = BASE_URL + "users?maxResults=500";
    private static final String PROJECT_URL = BASE_URL + "project/" + PROJECT;

    private static final String ISSUE_TYPES_URL = BASE_URL + "issuetype/project?projectId=10047";

    private final HttpClient httpClient;
    private final String jiraToken;
    private final String username;
    private final String sprintName;

    private SprintStart(String username, String sprintName) {
        this.username = username;
        this.httpClient = HttpClient.newHttpClient();
        this.jiraToken = System.getenv("JIRA_TOKEN");
        this.sprintName = sprintName;
    }

    /**
     * Creates review tickets for a sprint. Expects the environment variable
     * <code>JIRA_TOKEN</code> to be set.
     *
     * Expects the following command-line arguments to be set in order
     * <ol>
     *     <li>JIRA user email, e.g., janedoe@ucsc.edu, associated with the token</li>
     *     <li>sprint name</li>
     * </ol>
     * @param args
     */
    public static void main(String[] args)
        throws URISyntaxException, IOException, InterruptedException {
        final SprintStart sprintStart = new SprintStart(args[0], args[1]);
        sprintStart.createReviewTickets();

    }


    public void createReviewTickets() throws URISyntaxException, IOException, InterruptedException {
        final String users;
        final JiraUser[] jiraUsers =
            jiraRequest(USERS_URL, JiraUser[].class);
        try (InputStream inputStream = SprintStart.class.getResourceAsStream("/users.txt")) {
            users = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        Arrays.stream(jiraUsers).filter(jiraUser -> users.contains(jiraUser.displayName))
            .forEach(jiraUser -> System.out.println("jiraUser = " + jiraUser));

        final JiraProject jiraProject = jiraRequest(PROJECT_URL, JiraProject.class);
        System.out.println("jiraProject = " + jiraProject);

        final JiraIssueType[] jiraIssueTypes = jiraRequest(ISSUE_TYPES_URL, JiraIssueType[].class);
        final JiraIssueType taskIssueType = Arrays.stream(jiraIssueTypes)
            .filter(jiraIssueType -> "Task".equals(jiraIssueType.name())).findFirst().get();
        System.out.println("taskIssueType = " + taskIssueType);
    }

    private <T> T jiraRequest(String uriString, Class<T> clazz)
        throws URISyntaxException, IOException, InterruptedException {
        final URI uri = new URI(uriString);
        final HttpRequest httpRequest =
            HttpRequest.newBuilder().
                GET()
                .uri(uri)
                .header("Authorization", "Basic %s".formatted(Base64.getEncoder().encodeToString((username + ':'
                    + jiraToken).getBytes())))
                .build();
        final HttpResponse<String> httpResponse = httpClient.send(httpRequest, BodyHandlers.ofString());
        final Gson gson = new Gson();
        return gson.fromJson(httpResponse.body(), clazz);
    }

    record JiraUser(String accountId, String displayName) { }

    record JiraProject(String key, String id) { }

    record JiraIssueType(String name, String id) { }

}
