package io.dockstore.tooltester.helper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author gluu
 * @since 07/05/19
 */
public class GA4GHHelperTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void runnerSupportsDescriptorType() {
        Assert.assertTrue(GA4GHHelper.runnerSupportsDescriptorType("cwl-runner", ToolVersion.DescriptorTypeEnum.CWL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwl-runner", ToolVersion.DescriptorTypeEnum.WDL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwl-runner", ToolVersion.DescriptorTypeEnum.NFL));
        Assert.assertTrue(GA4GHHelper.runnerSupportsDescriptorType("cwltool", ToolVersion.DescriptorTypeEnum.CWL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwltool", ToolVersion.DescriptorTypeEnum.WDL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwltool", ToolVersion.DescriptorTypeEnum.NFL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cromwell", ToolVersion.DescriptorTypeEnum.CWL));
        Assert.assertTrue(GA4GHHelper.runnerSupportsDescriptorType("cromwell", ToolVersion.DescriptorTypeEnum.WDL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cromwell", ToolVersion.DescriptorTypeEnum.NFL));
    }

    @Test
    public void getTools() throws URISyntaxException, IOException {
        String json = resourceFilePathToString();
        List<Tool> allTools = Arrays.asList(objectMapper.readValue(json, Tool[].class));
        List<Tool> filteredTools1 = GA4GHHelper.filterTools(allTools, false, new ArrayList<>(), new ArrayList<>(), true, true);
        Assert.assertEquals(25, filteredTools1.size());
        List<Tool> filteredTools2 = GA4GHHelper.filterTools(allTools, true, new ArrayList<>(), new ArrayList<>(), false, true);
        Assert.assertEquals(18, filteredTools2.size());
        List<Tool> filteredTools = GA4GHHelper.filterTools(allTools, true, new ArrayList<>(), new ArrayList<>(), true, true);
        Assert.assertEquals(20, filteredTools.size());
    }

    private String resourceFilePathToString() throws IOException, URISyntaxException {
        Path path = Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource("GA4GHTools.json")).toURI());
        path.toFile();
        String fileContents;
        try (Stream<String> lines = Files.lines(path)) {
            fileContents = lines.collect(Collectors.joining("\n"));
        }
        return fileContents;
    }
}
