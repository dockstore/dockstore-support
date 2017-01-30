package io.dockstore.toolbackup.client.cli;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.tidy.Tidy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.System.out;
import static junit.framework.TestCase.assertEquals;

/**
 * Created by kcao on 25/01/17.
 */
public class ReportGeneratorTest extends Base {
    private static List<File> htmlFiles;
    private static List<File> jsonFiles;

    @BeforeClass
    public static void setUpFiles() {
        Client.main(new String[]{"--bucket-name", BUCKET, "--local-dir", DIR, "--test-mode-activate", "true", "--key-prefix", PREFIX});
        htmlFiles = ((List<File>) FileUtils.listFiles(new File(REPORT), new String[] { "html" }, false));
        jsonFiles = ((List<File>) FileUtils.listFiles(new File(REPORT), new String[] { "JSON" }, false));
    }

    @Test
    public void validateHTMLSyntax()  throws Exception {
        Tidy tidy = new Tidy();

        for(File file : htmlFiles) {
            try {
                tidy.parse(FileUtils.openInputStream(file), out);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(tidy.getParseErrors() > 0) {
                break;
            }
        }
        assertEquals(tidy.getParseErrors(), 0);
    }

    @Test
    public void validateReport() throws Exception {
        List<String> headings = new ArrayList<>();

        for(File file : htmlFiles) {
            Document doc = Jsoup.parse(file, "UTF-8");
            Elements thHeadings = doc.select("th");

            for(Element heading : thHeadings) {
                headings.add(heading.text());
            }
        }

        assertEquals(headings.stream().anyMatch(str -> str.contains("Meta-Version") || str.contains("Version") || str.contains("Size") || str.contains("Time") || str.contains("Availability")), true);
    }

    @Test
    public void validateJSONSyntax() throws Exception {
        //assertEquals(1, jsonFiles.size());

        Gson gson = new Gson();

        for(File file : jsonFiles) {
            gson.fromJson(FileUtils.readFileToString(file, "UTF-8"), Object.class);
        }
    }
}