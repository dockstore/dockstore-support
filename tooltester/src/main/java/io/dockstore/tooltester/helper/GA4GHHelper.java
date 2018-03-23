package io.dockstore.tooltester.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.swagger.client.ApiException;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;

import static io.dockstore.tooltester.helper.ExceptionHandler.API_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.CLIENT_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;

/**
 * @author gluu
 * @since 23/03/18
 */
public class GA4GHHelper {
    /**
     * Gets all tools from the GA4GH API
     * @param ga4GhApi  The GA4GH API
     * @return  A list of all tools from the GA4GH API
     */
    private static List<Tool> getAllTools(Ga4GhApi ga4GhApi) {
        List<Tool> tools = new ArrayList<>();
        try {
            tools = ga4GhApi.toolsGet(null, null, null, null, null, null, null, null,null);
        } catch (ApiException e) {
            exceptionMessage(e, "Could not get all tools", API_ERROR);
        }
        return tools;
    }

    /**
     * Filters the list of tools to only have tools that are verified and keep only the verified versions of each verified tool
     * @param tools A list of all tools
     * @return  A list of all verified tools with only verified versions
     */
    private static List<Tool> filterVerified(List<Tool> tools) {
        List<Tool> verifiedTools;
        verifiedTools = tools.parallelStream().filter(Tool::isVerified).collect(Collectors.toList());
        for (Tool tool : verifiedTools) {
            tool.setVersions(tool.getVersions().parallelStream().filter(ToolVersion::isVerified).collect(Collectors.toList()));
        }
        return verifiedTools;
    }

    /**
     * This function checks if the any of the tool's verified source matches the filter
     *
     * @param filter          The list of verified sources that we're interested in
     * @param verifiedSources The tool version's verified sources
     * @return True if the one of the verified sources matches the filter
     */
    private static boolean matchVerifiedSource(List<String> filter, String verifiedSources) {
        return filter.stream().anyMatch(str -> str.trim().equals(verifiedSources));

    }

    /**
     * Gets all the tools after filters applied which may be verified sources, toolnames, verified or not
     * Should be used by all client commands
     * @param ga4GhApi          The GA4GH API
     * @param verified          Whether to filter out all non-verified tools or not
     * @param verifiedSources   The only verified sources to get (requires verified to be true)
     * @param toolNames         The specific toolnames to retain
     * @return                  List of all tools after filters are applied
     */
    public static List<Tool> getTools(Ga4GhApi ga4GhApi, boolean verified, List<String> verifiedSources, List<String> toolNames) {
        List<Tool> tools =  getAllTools(ga4GhApi);
        if (!verified && !verifiedSources.isEmpty()) {
            ExceptionHandler.errorMessage("Searching for a unverified tool but have verified sources", CLIENT_ERROR);
        }
        if (verified) {
            tools = filterVerified(tools);
        }
        if (!verifiedSources.isEmpty()) {
            for (Tool tool : tools) {
                tool.setVersions(tool.getVersions().parallelStream().filter(p -> matchVerifiedSource(verifiedSources, p.getVerifiedSource()))
                        .collect(Collectors.toList()));
            }
        }
        if (!toolNames.isEmpty()) {
            tools = tools.parallelStream().filter(t -> toolNames.contains(t.getId())).collect(Collectors.toList());
        }
        return tools;
    }
}
