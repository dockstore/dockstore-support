package io.dockstore.tooltester.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.ToolVersion;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author gluu
 * @since 07/05/19
 */
public class GA4GHHelperTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void runnerSupportsDescriptorType() {
        assertTrue(GA4GHHelper.runnerSupportsDescriptorType("cwl-runner", ToolVersion.DescriptorTypeEnum.CWL));
        assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwl-runner", ToolVersion.DescriptorTypeEnum.WDL));
        assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwl-runner", ToolVersion.DescriptorTypeEnum.NFL));
        assertTrue(GA4GHHelper.runnerSupportsDescriptorType("cwltool", ToolVersion.DescriptorTypeEnum.CWL));
        assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwltool", ToolVersion.DescriptorTypeEnum.WDL));
        assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwltool", ToolVersion.DescriptorTypeEnum.NFL));
        assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cromwell", ToolVersion.DescriptorTypeEnum.CWL));
        assertTrue(GA4GHHelper.runnerSupportsDescriptorType("cromwell", ToolVersion.DescriptorTypeEnum.WDL));
        assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cromwell", ToolVersion.DescriptorTypeEnum.NFL));
    }

    // TODO: correct the issues in this test and have it run correctly
    @Disabled("When upgrading the APIs used in this test, the test broke")
    @Test
    public void getTools() throws URISyntaxException, IOException {
        String json = resourceFilePathToString();
        List<Tool> allTools = Arrays.asList(OBJECT_MAPPER.readValue(json, Tool[].class));
        List<Tool> filteredTools1 = GA4GHHelper.filterTools(allTools, false, new ArrayList<>(), new ArrayList<>(), true, true);
        assertEquals(25, filteredTools1.size());
        List<Tool> filteredTools2 = GA4GHHelper.filterTools(allTools, true, new ArrayList<>(), new ArrayList<>(), false, true);
        assertEquals(18, filteredTools2.size());
        List<Tool> filteredTools = GA4GHHelper.filterTools(allTools, true, new ArrayList<>(), new ArrayList<>(), true, true);
        assertEquals(20, filteredTools.size());
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
