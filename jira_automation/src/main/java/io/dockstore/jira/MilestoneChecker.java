package io.dockstore.jira;

import java.io.IOException;
import java.net.URISyntaxException;
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
 *
 * <p>The description of a GitHub issue is updated by Unito to look like this:</p>
 *
 * <pre>
 * ┆Issue is synchronized with this Jira Story
 * ┆Fix Versions: Dockstore 1.16
 * ┆Issue Number: DOCK-2519
 * ┆Sprint: 136 - Izmir
 * ┆Issue Type: Story
 * </pre>
 * 
 * <p>We can tell if JIRA fix version and GitHub milestone are out of sync by comparing the Fix Version string above with the
 * GitHub issue milestone.</p>
 */
public final class MilestoneChecker {

    /**
     *  Pattern for finding a substring like "Fix Versions: Dockstore 1.15" in GitHub issue body, to extract
     *  the "1.15"
      */
    private static final Pattern FIX_VERSIONS = Pattern.compile("((Fix Versions)|(fixVersions))(:\\s*(Dockstore )?(.*))");
    private static final Pattern JIRA_ISSUE = Pattern.compile("((Issue Number)|(friendlyId)): (DOCK-\\d+)");

    private MilestoneChecker() { }

    public static void main(String[] args) throws IOException {
        final List<JiraAndGithub> mismatchedIssues = findMismatchedIssues();
        if (mismatchedIssues.isEmpty()) {
            System.out.println("The JIRA fix version and GitHub milestone are in sync for all DOCK issues");
        } else {
            System.out.println("The following issues are mismatched:");
            mismatchedIssues.forEach(MilestoneChecker::printMismatchedIssue);
        }
        mismatchedIssues.forEach(issue -> {
            final GHIssue gitHubIssue = issue.ghIssue;
            final String body = gitHubIssue.getBody();
            final Matcher issueMatcher = JIRA_ISSUE.matcher(body);
            final boolean found = issueMatcher.find();
            if (!found) {
                System.out.println("Milestone in GitHub but not in JIRA, " + issue);
            } else {
                try {
                    final JiraIssue jiraIssue = Utils.getJiraIssue(issue.jiraIssue);
                    final FixVersion[] fixVersions = jiraIssue.fields().fixVersions();
                    if (fixVersions.length == 0) {
                        System.out.println("No fix version in JIRA, need to set it to " + gitHubIssue.getMilestone());
                    }
                    else if (fixVersions.length > 1) {
                        System.out.println("Too many fix versions in Jira = " + jiraIssue);
                    } else {
                        if (jiraIssue.fields().updated().after(gitHubIssue.getUpdatedAt())) {
                            System.out.println("Gotta update GitHub milestone = " + issue);
                        } else {
                            System.out.println("Gotta update jira issue fix version = " + issue);
                        }
                    }
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private static void printMismatchedIssue(final JiraAndGithub issue) {
        final GHIssue ghIssue = issue.ghIssue;
        System.out.println(
            "GitHub %s, milestone %s; JIRA issue %s, fix version %s".formatted(
                ghIssue.getNumber(),
                ghIssue.getMilestone().getTitle(),
                issue.jiraIssue,
                findJiraFixVersion(ghIssue.getBody())));
    }

    /**
     * Finds issues where the GitHub milestone does not match the JIRA fix version
     * @return
     * @throws IOException
     */
    private static List<JiraAndGithub> findMismatchedIssues() throws IOException {
        final List<GHIssue> openIssues = Utils.findOpenIssues(Utils.getDockstoreRepository());
        return openIssues.stream()
            .filter(MilestoneChecker::milestoneAndFixVersionMismatch)
            .map(ghIssue -> {
                // If empty, Unito hasn't synced yet
                return Utils.findJiraIssueInBody(ghIssue)
                    .map(jiraIssue -> new JiraAndGithub(jiraIssue, ghIssue)).orElse(null);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private static boolean milestoneAndFixVersionMismatch(final GHIssue ghIssue) {
        final GHMilestone milestone = ghIssue.getMilestone();
        final String body = ghIssue.getBody();
        final String jiraFixVersion = findJiraFixVersion(body);
        if (jiraFixVersion != null) {
            if (milestone == null) {
                // There's a fix version in JIRA, but none in GitHub
                return true;
            }
            return !milestoneAndFixVersionEqual(jiraFixVersion, milestone.getTitle());
        } else { // No fix version in JIRA, is there one in GitHub?
            return milestone != null;
        }
    }

    private static String generateJiraIssuesUrl(final List<JiraAndGithub> issues) {
        return "https://ucsc-cgl.atlassian.net/issues/?jql=project=DOCK AND "
            + issues.stream().map(issue -> "key=\"" + issue.jiraIssue() + "\"")
                .collect(Collectors.joining(" or "));
    }

    private static String generateGitHubIssuesUrl(final List<JiraAndGithub> issues) {
        return "https://github.com/dockstore/dockstore/issues?q="
            + issues.stream().map(issue -> "" + issue.ghIssue.getNumber())
            .collect(Collectors.joining("+"));
    }

    private static boolean milestoneAndFixVersionEqual(String jiraFixVersion, String milestone) {
        return Utils.JIRA_OPEN_ENDED_RESEARCH_TASKS.equals(jiraFixVersion) && Utils.GITHUB_OPEN_ENDED_RESEARCH_TASKS.equals(milestone)
            || Objects.equals(jiraFixVersion, milestone);
    }

    private static String findJiraFixVersion(String gitHubIssueBody) {
        final Matcher matcher = FIX_VERSIONS.matcher(gitHubIssueBody);
        if (matcher.find()) {
            return matcher.group(6);
        }
        return null;
    }

    record JiraAndGithub(String jiraIssue, GHIssue ghIssue) { }
}
