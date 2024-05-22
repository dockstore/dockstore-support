package io.dockstore.jira;

import java.util.Date;

public record JiraIssue(String key, Fields fields) { }

record Fields(Date updated, FixVersion[] fixVersions) { }

record FixVersion(String name) { }
