package io.dockstore.jira;

import java.io.IOException;
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

    private static final Pattern JIRA_ISSUE_IN_GITHUB_BODY = Pattern.compile("((Issue Number)|(friendlyId)): (DOCK-\\d+)");
    private static final int JIRA_ISSUE_GROUP = 4;

    private Utils() { }

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

}
