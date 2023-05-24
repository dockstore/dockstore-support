package io.dockstore.toolbackup.client.cli;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.TAG;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.TIME;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.TOOL_NAME;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import io.dockstore.toolbackup.client.cli.common.DirCleaner;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Created by kcao on 25/01/17.
 */
public class ReportGeneratorTest {

    private static final List<VersionDetail> VERSION_DETAILS = new ArrayList<>();

    @BeforeClass
    public static void setUpFiles() {
        DirectoryGenerator.createDir(DIR);
        VERSION_DETAILS.add(new VersionDetail(TAG, TIME, 2000, 0, TIME, true, DIR));
    }

    @Test
    public void generateJSONMap() throws Exception {
        Map<String, List<VersionDetail>> toolsToVersions = new HashMap<>();
        toolsToVersions.put(TOOL_NAME, VERSION_DETAILS);

        ReportGenerator.generateJSONMap(toolsToVersions, DIR);

        Gson gson = new Gson();
        File map = new File(DIR + File.separator + "map.JSON");
        gson.fromJson(FileUtils.readFileToString(map, "UTF-8"), Object.class);
    }

    @Test
    public void generateToolReport() throws Exception {
        List<String> headings = new ArrayList<>();

        File html = new File(DIR + File.separator + "tool.html");
        FileUtils.writeStringToFile(html, ReportGenerator.generateToolReport(VERSION_DETAILS), "UTF-8");

        Document doc = Jsoup.parse(html, "UTF-8");
        Elements thHeadings = doc.select("th");

        for (Element heading : thHeadings) {
            headings.add(heading.text());
        }

        assertTrue(headings.stream().anyMatch(str -> str.contains("Meta-Version") || str.contains("Version") || str.contains("Size") || str.contains("Time") || str.contains("Availability")));
    }

    @Test
    public void generateMainMenu() throws Exception {
        List<String> rowsText = new ArrayList<>();

        Set<String> tools = new HashSet<>();
        tools.add(TOOL_NAME);

        File html = new File(DIR + File.separator + "index.html");
        FileUtils.writeStringToFile(html, ReportGenerator.generateMainMenu(tools, 1000, 2000), "UTF-8");

        Document doc = Jsoup.parse(html, "UTF-8");
        Elements rows = doc.select("td");

        for (Element row : rows) {
            rowsText.add(row.text());
        }

        assertTrue(rowsText.stream().anyMatch(str -> str.contains(TOOL_NAME)));
    }

    @AfterClass
    public static void cleanUpDIR() {
        DirCleaner.deleteDir(DIR);
    }
}
