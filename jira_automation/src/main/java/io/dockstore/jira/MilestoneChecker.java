package io.dockstore.jira;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHMilestone;

/**
 * Generates a GitHub url with all dockstore issues whose milestone does not match the fix version
 * in JIRA. Works by looking at all open GitHub issues, then reading the info Unito appends to the
 * description in GitHub, which includes the fix version. It compares the fix version in JIRA with
 * the milestone of the GitHub issue.
 */
public final class MilestoneChecker {

    /**
     *  Pattern for finding a substring like "Fix Versions: Dockstore 1.15" in GitHub issue body, to extract
     *  the "1.15"
      */
    private static final Pattern FIX_VERSIONS = Pattern.compile("((Fix Versions)|(fixVersions))(:\\s*(Dockstore )?(.*))");

    private MilestoneChecker() { }

    public static void main(String[] args) throws IOException {
        final List<GHIssue> openIssues = Utils.findOpenIssues(Utils.getDockstoreRepository());
        final List<JiraAndGithub> issues = openIssues.stream()
            .filter(ghIssue -> {
                final GHMilestone milestone = ghIssue.getMilestone();
                final String body = ghIssue.getBody();
                final Matcher matcher = FIX_VERSIONS.matcher(body);
                if (matcher.find()) {
                    if (milestone == null) {
                        // There's a fix version in JIRA, but none in GitHub
                        return true;
                    }
                    final String jiraFixVersion = matcher.group(6);
                    return !milestoneAndFixVersionEqual(jiraFixVersion, milestone.getTitle());
                } else {
                    // No fix version in JIRA, is there one in Dockstore?
                    return milestone != null;
                }
            })
            .map(ghIssue -> new JiraAndGithub(Utils.findJiraIssueInBody(ghIssue).get(), ghIssue.getNumber()))
            .collect(Collectors.toList());
        System.out.println(generateGitHubIssuesUrl(issues));
        System.out.println();
        System.out.println(generateJiraIssuesUrl(issues));
    }

    private static String generateJiraIssuesUrl(final List<JiraAndGithub> issues) {
        return "https://ucsc-cgl.atlassian.net/issues/?jql=project=DOCK AND "
            + issues.stream().map(issue -> "key=\"" + issue.jiraIssue() + "\"")
                .collect(Collectors.joining(" or "));
    }

    private static String generateGitHubIssuesUrl(final List<JiraAndGithub> issues) {
        return "https://github.com/dockstore/dockstore/issues?q="
            + issues.stream().map(issue -> "" + issue.githubIssue())
            .collect(Collectors.joining("+"));
    }

    private static boolean milestoneAndFixVersionEqual(String jiraFixVersion, String milestone) {
        return Utils.JIRA_OPEN_ENDED_RESEARCH_TASKS.equals(jiraFixVersion) && Utils.GITHUB_OPEN_ENDED_RESEARCH_TASKS.equals(milestone)
            || Objects.equals(jiraFixVersion, milestone);
    }

    record JiraAndGithub(String jiraIssue, int githubIssue) { }
}
