package io.dockstore.jira;

import java.io.IOException;
import java.util.List;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public class Utils {

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
        final List<GHIssue> prsAndIssues = repository.getIssues(GHIssueState.OPEN);
        return prsAndIssues.stream()
            .filter(ghIssue -> !ghIssue.isPullRequest())
            .toList();
    }

}
