package io.dockstore.jira;

import java.util.Date;

/**
 * JIRA Rest API models. There is no OpenAPI definition nor Java library I could find that worked;
 * this models the parts of the entities that we consume, e.g., <code>JiraIssue</code> has many
 * more properties than the record has here, but we only need to access the ones in the record.
 * @param key
 * @param fields
 */
public record JiraIssue(String key, Fields fields) { }

record Fields(Date updated, FixVersion[] fixVersions) { }

record FixVersion(String name) { }

record UpdateJiraIssue(UpdateFields fields) { }
record UpdateFields(FixVersion[] fixVersions) { }
