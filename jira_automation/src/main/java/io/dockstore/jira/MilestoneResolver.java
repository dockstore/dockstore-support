package io.dockstore.jira;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHMilestone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attempts to resolve JIRA DOCK ticket fix version mismatches with the corresponding GitHub milestone.
 * Handles these cases:
 *
 * <ol>
 *     <li>There is a JIRA fix version but no GitHub milestone -- sets the GitHub milestone to the JIRA fix version</li>
 *     <li>There is a GitHub milestone but no JIRA fix version -- sets the JIRA fix version to the GitHub milestone</li>
 * </ol>
 *
 * <p>It does not handle the case of the JIRA fix version not matching the GitHub milestone. It does
 * print out a message saying the mismatch needs to be resolved manually. To resolve this automatically,
 * the program would need to:</p>
 *
 * <ol>
 *     <li>Figure out the timestamp of when the JIRA fix version was last set</li>
 *     <li>Figure out the timestamp of when the GitHub milestone was set</li>
 *     <li>Resolve the difference by using the most recently changed.</li>
 * </ol>
 *
 * <p>This is possible, but didn't seem worth the extra work at this point. See
 * https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issues/#api-rest-api-3-issue-issueidorkey-changelog-get to get
 * the JIRA change log. Note that it is paginated so the invoker would have to account for that.</p>
 *
 */
public final class MilestoneResolver {

    /**
     *  Pattern for finding a substring like "Fix Versions: Dockstore 1.15" in GitHub issue body, to extract
     *  the "1.15"
     */
    private static final Pattern FIX_VERSIONS = Pattern.compile("((Fix Versions)|(fixVersions))(:\\s*(Dockstore )?(.*))");
    private static final int FIX_VERSION_REG_EX_GROUP = 6;

    private static final Logger LOG = LoggerFactory.getLogger(MilestoneResolver.class);

    private MilestoneResolver() { }

    public static void main(String[] args) throws IOException {
        final List<JiraAndGithub> mismatchedIssues = findMismatchedIssues();
        if (mismatchedIssues.isEmpty()) {
            System.out.println("The JIRA fix version and GitHub milestone are in sync for all DOCK issues");
        } else {
            System.out.println("The following issues are mismatched:");
            mismatchedIssues.forEach(MilestoneResolver::printMismatchedIssue);
        }
        mismatchedIssues.forEach(issue -> {
            final GHIssue gitHubIssue = issue.ghIssue;
            try {
                final JiraIssue jiraIssue = Utils.getJiraIssue(issue.jiraIssueId);
                final GHMilestone ghMilestone = gitHubIssue.getMilestone();
                final String jiraIssueUrl = getJiraIssueUrl(jiraIssue.key());
                System.out.println("Processing JIRA issue %s".formatted(jiraIssueUrl));
                final FixVersion[] fixVersions = jiraIssue.fields().fixVersions();
                if (fixVersions.length == 0) {
                    // There is GitHub milestone but no JIRA fix version
                    updateJiraIssue(jiraIssue.key(), ghMilestone.getTitle());
                } else if (fixVersions.length == 1 && ghMilestone == null) {
                    // There's a JIRA fix version, but no GitHub milestone, set the GitHub milestone
                    updateGitHubMilestone(gitHubIssue.getNumber(), fixVersions[0].name());
                } else {
                    System.out.println("The fix version and milestone mismatch must be resolved manually for: %s".formatted(getJiraIssueUrl(issue.jiraIssueId)));
                }
            } catch (URISyntaxException | IOException | InterruptedException e) {
                LOG.error("Error resolving %s".formatted(issue), e);
                throw new RuntimeException(e);
            }
        });
    }

    private static void updateGitHubMilestone(int gitHubIssue, String jiraFixVersion) {
        if (Utils.updateGitHubMilestone(gitHubIssue, jiraFixVersion)) {
            System.out.println("Updated GitHub milestone in %s to %s".formatted(gitHubIssue, jiraFixVersion));
        } else {
            System.err.println("Failed to update GitHub milestone in %s".formatted(gitHubIssue));
        }
    }

    private static void updateJiraIssue(String jiraIssue, String gitHubMilestone)
        throws URISyntaxException, IOException, InterruptedException {
        final String jiraIssueUrl = getJiraIssueUrl(jiraIssue);
        if (Utils.updateJiraFixVersion(jiraIssue, gitHubMilestone)) {
            System.out.println("Updated fix version in %s to %s".formatted(jiraIssueUrl,
                gitHubMilestone));
        } else {
            System.err.println("Failed to update fix version in %s".formatted(jiraIssueUrl));
        }
    }

    private static void printMismatchedIssue(final MilestoneResolver.JiraAndGithub issue) {
        final GHIssue ghIssue = issue.ghIssue;
        final GHMilestone ghMilestone = ghIssue.getMilestone();
        final String notSet = "<not set>";
        final String milestone = ghMilestone != null ? ghMilestone.getTitle() : notSet;
        final String jiraFixVersion = findJiraFixVersion(ghIssue.getBody()).orElse(notSet);
        System.out.println(
            "GitHub %s, milestone %s; JIRA %s, fix version %s".formatted(
                ghIssue.getNumber(),
                milestone,
                issue.jiraIssueId,
                jiraFixVersion));
    }

    /**
     * Finds issues where the GitHub milestone does not match the JIRA fix version
     * @return
     * @throws IOException
     */
    private static List<MilestoneResolver.JiraAndGithub> findMismatchedIssues() throws IOException {
        final List<GHIssue> openIssues = Utils.findOpenIssues(Utils.getDockstoreRepository());
        return openIssues.stream()
            .filter(MilestoneResolver::milestoneAndFixVersionMismatch)
            .map(ghIssue -> {
                // If empty, Unito hasn't synced, JIRA issue does not yet exist
                return Utils.findJiraIssueInBody(ghIssue)
                    .map(jiraIssue -> new MilestoneResolver.JiraAndGithub(jiraIssue, ghIssue)).orElse(null);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private static boolean milestoneAndFixVersionMismatch(final GHIssue ghIssue) {
        final GHMilestone milestone = ghIssue.getMilestone();
        final String body = ghIssue.getBody();
        final Optional<String> jiraFixVersion = findJiraFixVersion(body);
        if (jiraFixVersion.isPresent()) {
            if (milestone == null) {
                // There's a fix version in JIRA, but none in GitHub
                return true;
            }
            return !milestoneAndFixVersionEqual(jiraFixVersion.get(), milestone.getTitle());
        } else { // No fix version in JIRA, is there one in GitHub?
            return milestone != null;
        }
    }

    private static String generateJiraIssuesUrl(final List<MilestoneResolver.JiraAndGithub> issues) {
        return "https://ucsc-cgl.atlassian.net/issues/?jql=project=DOCK AND "
            + issues.stream().map(issue -> "key=\"" + issue.jiraIssueId() + "\"")
            .collect(Collectors.joining(" or "));
    }

    private static String getJiraIssueUrl(String issueNumber) {
        return "https://ucsc-cgl.atlassian.net/browse/%s".formatted(issueNumber);
    }

    private static String generateGitHubIssuesUrl(final List<MilestoneResolver.JiraAndGithub> issues) {
        return "https://github.com/dockstore/dockstore/issues?q="
            + issues.stream().map(issue -> "" + issue.ghIssue.getNumber())
            .collect(Collectors.joining("+"));
    }

    private static boolean milestoneAndFixVersionEqual(String jiraFixVersion, String milestone) {
        return Utils.JIRA_OPEN_ENDED_RESEARCH_TASKS.equals(jiraFixVersion) && Utils.GITHUB_OPEN_ENDED_RESEARCH_TASKS.equals(milestone)
            || Objects.equals(jiraFixVersion, milestone);
    }

    private static Optional<String> findJiraFixVersion(String gitHubIssueBody) {
        final Matcher matcher = FIX_VERSIONS.matcher(gitHubIssueBody);
        if (matcher.find()) {
            return Optional.of(matcher.group(FIX_VERSION_REG_EX_GROUP));
        }
        return Optional.empty();
    }

    record JiraAndGithub(String jiraIssueId, GHIssue ghIssue) { }
}
