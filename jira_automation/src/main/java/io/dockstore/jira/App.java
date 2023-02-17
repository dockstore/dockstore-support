package io.dockstore.jira;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;

/**
 * Generates a JIRA JQL query to find JIRA issues closed by Unito that are open in JIRA.
 *
 * Does not generate a url, because the url will be too long for the browser; you need to input
 * the query in JIRA so it can be POSTed.
 */
public class App
{
    private final static Pattern JIRA_ISSUE_IN_GITHUB_BODY = Pattern.compile("((Issue Number)|(friendlyId)): (DOCK-\\d+)");
    public static void main( String[] args ) throws IOException {
        final GHRepository repository = Utils.getDockstoreRepository();
        final String jqlQuery = issuesOpenInGitHubButDoneInJiraQuery(repository);
        System.out.println(jqlQuery);
    }

    private static String issuesOpenInGitHubButDoneInJiraQuery(final GHRepository repository) throws IOException {
        final List<GHIssue> issues = Utils.findOpenIssues(repository);
        final String jiraIssues = issues.stream()
            .map(ghIssue -> {
                final Matcher matcher = JIRA_ISSUE_IN_GITHUB_BODY.matcher(ghIssue.getBody());
                if (matcher.find()) {
                    return "\"" + matcher.group(4) + "\"";
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.joining(", "));

        return """
            project = "DOCK" and key in ( %s ) and status = Done ORDER BY created DESC
            """.formatted(jiraIssues);
    }
}
