There are three applications in here to facilitate our JIRA/GitHub interaction

1. io.dockstore.jira.MilestoneChecker - generates GitHub and JQL queries to find mismatches in the JIRA fix version and GitHub milestone. The JIRA fix version is
   a multi-value field; the GitHub milestone is a single-value field, so Unito doesn't sync them. We have to remember to manually
   keep them in sync; this program identifies cases we've missed.
2. io.dockstore.jira.MilestoneResolver - Updates JIRA and GitHub
3. SprintStart - a barely started work in progress to automatically generate review tickets at the beginning of a sprint, which
   is currently a manual and tedious process.
4. io.dockstore.jira.ResolutionChecker - used to help find issues open in GitHub that are closed in JIRA. This was to diagnose an issue where 
Unito was seemingly mysteriously closing JIRA issues at random. It turned out to be because we hadn't properly configured
a GitHub and JIRA user in Unito -- it's the Unito intended behavior. We currently don't need to run this, although if we have
a configuration issue again, it could be useful in the future.

# Auth

* ResolutionChecker, MilestoneChecker, and MilestoneResolver require the environment variable `GITHUB_TOKEN` be set to a GitHub personal access token that has access to dockstore GitHub issues
* SprintStart and MilestoneResolver require the environment variable `JIRA_TOKEN` be set to a JIRA token.

# Usage

I usually run in IntelliJ with a Run Configuration

1. In Run Configuration, set the main class to io.dockstore.jira.MilestoneChecker or io.dockstore.jira.ResolutionChecker
2. Add the environment variable `GITHUB_TOKEN` to your GitHub token.
3. For MilestoneResolver, you also need to set these environment variables:
    * `JIRA_USERNAME` to your JIRA user, e.g., jdoe@ucsc.edu
    * `JIRA_TOKEN` to your JIRA token
3. The console will print out generated urls, which you then paste into your browser.

