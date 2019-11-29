package io.dockstore.tooltester.helper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.dockstore.tooltester.CommandObject;
import io.dockstore.tooltester.blacklist.BlackList;
import io.swagger.client.ApiException;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.tooltester.helper.ExceptionHandler.API_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.CLIENT_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;

/**
 * A variety of helper methods to filter TRS Tool and TRS ToolVersion
 * @author gluu
 * @since 23/03/18
 */
public class GA4GHHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(GA4GHHelper.class);
    /**
     * Gets all tools from the GA4GH API
     * @param ga4GhApi  The GA4GH API
     * @return  A list of all tools from the GA4GH API
     */
    private static List<Tool> getAllTools(Ga4GhApi ga4GhApi) {
        List<Tool> tools = new ArrayList<>();
        try {
            tools = ga4GhApi.toolsGet(null, null, null, null, null, null, null, null,null, null, null);
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
    public static List<Tool> getTools(Ga4GhApi ga4GhApi, boolean verified, List<String> verifiedSources, List<String> toolNames,
            boolean checker, boolean blacklist) {
        List<Tool> allTools = getAllTools(ga4GhApi);
        return filterTools(allTools, verified, verifiedSources, toolNames, checker, blacklist);

    }

    /**
     * Given all the TRS tools, filter them base on the flags provided
     *
     * @param allTools        All the TRS tools
     * @param verified        Whether to get only verified tools or not
     * @param verifiedSources If getting verified tools only, whether to only get the ones that are specific to certain verified sources
     * @param toolNames       If not null or empty, only return the tools with matching tool names
     * @param checker         Whether to also get the corresponding checker workflow (all previous filters do not apply for this checker workflow)
     * @param blacklist       Whether to filter out blacklisted tool versions or not
     * @return Filtered list of TRS tools
     */
    static List<Tool> filterTools(List<Tool> allTools, boolean verified, List<String> verifiedSources, List<String> toolNames,
            boolean checker, boolean blacklist) {
        List<Tool> filteredTools = allTools;
        if (!verified && !verifiedSources.isEmpty()) {
            ExceptionHandler.errorMessage("Searching for a unverified tool but have verified sources", CLIENT_ERROR);
        }
        if (verified) {
            filteredTools = filterVerified(allTools);
        }
        if (toolNames != null && !verifiedSources.isEmpty()) {
            for (Tool tool : filteredTools) {
                tool.setVersions(
                        tool.getVersions().parallelStream().filter(p -> matchVerifiedSource(verifiedSources, p.getVerifiedSource()))
                                .collect(Collectors.toList()));
            }
        }
        if (toolNames != null && !toolNames.isEmpty()) {
            filteredTools = filteredTools.parallelStream().filter(t -> toolNames.contains(t.getId())).collect(Collectors.toList());
        }
        if (blacklist) {
            filteredTools.forEach(tool -> tool.setVersions(tool.getVersions().stream().filter(version -> BlackList.isNotBlacklisted(tool.getId(), version.getName()))
                    .collect(Collectors.toList())));
        }
        if (checker) {
            addCheckerWorkflows(filteredTools, allTools);
        }
        filteredTools = filteredTools.stream().filter(tool -> {
            List<ToolVersion> versions = tool.getVersions();
            return (versions != null && !versions.isEmpty());
        }).collect(Collectors.toList());
        return filteredTools;
    }

    /**
     * Modifies filteredTools to also include its checkers
     *
     * @param filteredTools Previous list of tools that have been filtered already (by verification, etc)
     * @param allTools      List of all GA4GH tools
     */
    private static void addCheckerWorkflows(List<Tool> filteredTools, List<Tool> allTools) {
        List<Tool> filteredToolsWithChecker = filteredTools.stream().filter(Tool::isHasChecker).collect(Collectors.toList());

        String segment = "api/ga4gh/v2/tools/";
        filteredToolsWithChecker.forEach(tool -> {
            String checkerUrl = tool.getCheckerUrl();
            List<String> versionNames = tool.getVersions().stream().map(ToolVersion::getName).collect(Collectors.toList());
            String checkerId;
            checkerId = URLDecoder.decode(checkerUrl.substring(checkerUrl.indexOf(segment) + segment.length()), StandardCharsets.UTF_8);
            if (filteredTools.stream().noneMatch(tool1 -> tool1.getId().equals(checkerId))) {
                Optional<Tool> first = allTools.stream().filter(tool2 -> tool2.getId().equals(checkerId)).findFirst();
                if (first.isPresent()) {
                    Tool checkerToolWithAllVersions = first.get();
                    checkerToolWithAllVersions.setVersions(checkerToolWithAllVersions.getVersions().stream()
                            .filter(checkerToolVersions -> versionNames.contains(checkerToolVersions.getName()))
                            .collect(Collectors.toList()));
                    filteredTools.add(checkerToolWithAllVersions);
                } else {
                    LOGGER.error("There is supposed to be a published checker workflow from the TRS: " + checkerId);
                }
            } else {
                LOGGER.info("Checker workflow was already added");
            }
        });
    }

    /**
     * Determines whether the runner and descriptor type is compatible with each other
     *
     * @param runner             The runners available ("cwltool", "cwl-runner", "cromwell"
     * @param descriptorTypeEnum The descriptor type of the ToolVersion
     * @return Whether the runner can be used for the descriptor type
     */
    static boolean runnerSupportsDescriptorType(String runner, ToolVersion.DescriptorTypeEnum descriptorTypeEnum) {
        switch (runner) {
        case "cwltool":
        case "cwl-runner":
            return descriptorTypeEnum == ToolVersion.DescriptorTypeEnum.CWL;
        case "cromwell":
            return descriptorTypeEnum.equals(ToolVersion.DescriptorTypeEnum.WDL);
        default:
            return false;
        }
    }

    /**
     * Given a tool and the set of runners, this will give every valid combination of ToolId, ToolVersion, and runner with the following filters applied
     * 1. The Tool and its ToolVersion is not blacklisted
     * 2. The runner supports that version's descriptor types
     * 3. The ToolVersion is verified
     * TODO: Reuse this for all commands
     * @param tool  The TRS Tool
     * @param runners   The set of runners that ToolTester is supposed to take action on
     * @return      The combination of ToolId, ToolVersion, and runner to take action on
     */
    public static List<CommandObject> getCommandObjects(Tool tool, String[] runners) {
        String toolId = tool.getId();
        List<CommandObject> commandObjects = new ArrayList<>();
        tool.getVersions().stream()
                .forEach(toolVersion -> Arrays.stream(runners).forEach(runner -> {
                    if (toolVersion.getDescriptorType().stream()
                            .anyMatch(descriptorTypeEnum -> runnerSupportsDescriptorType(runner, descriptorTypeEnum))) {
                        commandObjects.add(new CommandObject(toolId, toolVersion, runner));
                    }
                }));
        return commandObjects;
    }
}
