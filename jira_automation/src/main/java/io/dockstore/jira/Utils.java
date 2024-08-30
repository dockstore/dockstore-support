package io.dockstore.jira;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Utils {

    public static final String JIRA_REST_BASE_URL = "https://ucsc-cgl.atlassian.net/rest/api/3/";
    public static final String GITHUB_OPEN_ENDED_RESEARCH_TASKS = "Open ended research tasks";
    public static final String JIRA_OPEN_ENDED_RESEARCH_TASKS = "Open-ended research tasks";
    private static final Pattern JIRA_ISSUE_IN_GITHUB_BODY = Pattern.compile("((Issue Number)|(friendlyId)): (DOCK-\\d+)");
    private static final int JIRA_ISSUE_GROUP = 4;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String JIRA_USERNAME = "JIRA_USERNAME";
    private static final String JIRA_TOKEN = "JIRA_TOKEN";
    private static final Gson GSON = new Gson();

    private static final Logger LOG = LoggerFactory.getLogger(Utils.class);


    private Utils() { }

    public static JiraIssue getJiraIssue(String issueId)
        throws URISyntaxException, IOException, InterruptedException {
        final URI uri = getJiraIssueRestUri(issueId);
        final HttpRequest httpRequest = authorizedRequestBuilder()
            .GET()
            .uri(uri)
            .build();
        final HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, BodyHandlers.ofString());
        final String body = response.body();
        final JiraIssue jiraIssue = GSON.fromJson(body, JiraIssue.class);
        return jiraIssue;
    }

    private static URI getJiraIssueRestUri(final String issueId) throws URISyntaxException {
        return new URI(JIRA_REST_BASE_URL + "issue/" + issueId);
    }


    public static boolean updateJiraFixVersion(String issueId, String gitHubMilestone)
        throws URISyntaxException, IOException, InterruptedException {
        final URI uri = getJiraIssueRestUri(issueId);
        final UpdateJiraIssue updateJiraIssue = new UpdateJiraIssue(new UpdateFields(
            jiraFixVersionFromGitHubMilestone(gitHubMilestone)));
        final String json = new Gson().toJson(updateJiraIssue);
        final HttpRequest httpRequest = authorizedRequestBuilder()
            .uri(uri)
            .PUT(BodyPublishers.ofString(json))
            .header("Content-type", "application/json")
            .build();
        final HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, BodyHandlers.ofString());
        return response.statusCode() < HttpURLConnection.HTTP_MULT_CHOICE;
    }

    private static FixVersion[] jiraFixVersionFromGitHubMilestone(final String gitHubMilestone) {
        if (gitHubMilestone == null) {
            return new FixVersion[0];
        }
        return new FixVersion[] {new FixVersion(gitHubMilestoneToJiraVersion(gitHubMilestone))};
    }

    public static boolean updateGitHubMilestone(int number, String jiraFixVersion)  {
        try {
            final GHIssue issue = getDockstoreRepository().getIssue(number);
            final String ghMilestoneDesc = jiraVersionToGitHubMilestone(jiraFixVersion);
            final PagedIterable<GHMilestone> ghMilestones =
                getDockstoreRepository().listMilestones(GHIssueState.ALL);
            final Optional<GHMilestone> milestone = ghMilestones.toList().stream()
                .filter(ghMilestone -> ghMilestoneDesc.equals(ghMilestone.getTitle()))
                .findFirst();
            if (milestone.isEmpty()) {
                System.err.println("Could not find GitHub milestone for %s".formatted(jiraFixVersion));
                return false;
            }
            issue.setMilestone(milestone.get());
        } catch (IOException e) {
            LOG.error("Error setting milestone on issue %s".formatted(number), e);
            System.err.println("Error updating milestone for GitHub issue %s".formatted(number));
            return false;
        }
        return true;
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

    /**
     * Converts a GitHub Milestone a JIRA fix version. Generally, the JIRA fix version is the milestone
     * preceded by Dockstore, e.g., the 1.16 GitHub milestone becomes "Dockstore 1.16" JIRA
     * fix version. The one exception is "Open ended research tasks" in GitHub becomes
     * "Open-ended research tasks" (note hyphen).
     * @param githubMilestone
     * @return
     */
    private static String gitHubMilestoneToJiraVersion(String githubMilestone) {
        if (githubMilestone == null) {
            return null;
        } else if (GITHUB_OPEN_ENDED_RESEARCH_TASKS.equals(githubMilestone)) {
            return JIRA_OPEN_ENDED_RESEARCH_TASKS;
        }
        return "Dockstore %s".formatted(githubMilestone);
    }

    private static String jiraVersionToGitHubMilestone(String jiraVersion) {
        final String prefix = "Dockstore ";
        if (jiraVersion.startsWith(prefix)) {
            return jiraVersion.substring(prefix.length());
        } else if (JIRA_OPEN_ENDED_RESEARCH_TASKS.equals(jiraVersion)) {
            return GITHUB_OPEN_ENDED_RESEARCH_TASKS;
        }
        System.err.println("Unexpected jiraVersion: %s".formatted(jiraVersion));
        return jiraVersion;
    }


}
