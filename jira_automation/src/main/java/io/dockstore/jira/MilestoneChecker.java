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
 * in JIRA.
 */
public class MilestoneChecker {

    /**
     *  Pattern for finding a substring like "Fix Versions: Dockstore 1.15" in GitHub issue body, to extract
     *  the "1.15"
      */
    private final static Pattern FIX_VERSIONS = Pattern.compile("((Fix Versions)|(fixVersions))(:\\s*(Dockstore )?(.*))");

    public static void main(String[] args) throws IOException {
        final List<GHIssue> openIssues = Utils.findOpenIssues(Utils.getDockstoreRepository());
        final String mismatches = openIssues.stream()
            .filter(ghIssue -> {
                final GHMilestone milestone = ghIssue.getMilestone();
                final String body = ghIssue.getBody();
                final Matcher matcher = FIX_VERSIONS.matcher(body);
                if (matcher.find()) {
                    if (milestone == null) {
                        return true;
                    }
                    final String jiraFixVersion = matcher.group(6);
                    return !milestoneAndFixVersionEqual(jiraFixVersion, milestone.getTitle());
                } else {
                    // No fix version in JIRA, is there one in Dockstore?
                    return milestone != null;
                }
            })
            .map(ghIssue -> String.valueOf(ghIssue.getNumber()))
            .collect(Collectors.joining("+"));
        System.out.println("https://github.com/dockstore/dockstore/issues?q=" + mismatches);
    }

    private static boolean milestoneAndFixVersionEqual(String jiraFixVersion, String milestone) {
        return "Open-ended research tasks".equals(jiraFixVersion) && milestone.equals("Open ended research tasks")
            || Objects.equals(jiraFixVersion, milestone);
    }
}
