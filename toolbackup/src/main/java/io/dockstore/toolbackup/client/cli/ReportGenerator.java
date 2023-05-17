package io.dockstore.toolbackup.client.cli;

import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleFunction;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Created by kcao on 11/01/17.
 */
final class ReportGenerator {

    private static final double NUM_B_IN_GB = 1e9;
    private static final double DOUBLE_SPECIAL_NUM = 100.0;
    private static final String STYLE;

    static {

        STYLE = "<!DOCTYPE html><html>" + "<head>"
            + "<style type=\"text/css\">\n"
            + "\t\t.err { color: #ff0000; }\n"
            + "\t\ttable { font-family: arial, sans-serif; border-collapse: collapse; width: 100%; }\n"
            + "\t\ttd, th { border: 1px solid #dddddd; text-align: left; padding: 8px; }\n"
            + "\t</style><title></title></head>"
            + "<body><table>";
    }

    private ReportGenerator() {
        // hidden constructor
    }



    private static double bToGB(long sizeInB) {
        DoubleFunction<Double> bytesToGB = (bytes) -> bytes / NUM_B_IN_GB;
        DoubleFunction<Double> format2DecPlaces = (num) -> Math.round(num * DOUBLE_SPECIAL_NUM) / DOUBLE_SPECIAL_NUM;
        return format2DecPlaces.apply(bytesToGB.apply(sizeInB));
    }

    //-----------------------JSON map generation and load-----------------------

    static void generateJSONMap(Map<String, List<VersionDetail>> toolsToVersions, String basePath) {
        List<JSONMapper> mapperList = new ArrayList<>();
        for (Map.Entry<String, List<VersionDetail>> entry : toolsToVersions.entrySet()) {
            JSONMapper jsonMapper = new JSONMapper(entry.getKey(), entry.getValue());
            mapperList.add(jsonMapper);
        }

        File file = new File(basePath + File.separator + "map.JSON");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            FileUtils.writeStringToFile(file, gson.toJson(mapperList), "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("Could not create or add to map.JSON");
        }
    }

    static Map<String, List<VersionDetail>> loadJSONMap(String basePath) {
        Map<String, List<VersionDetail>> toolsToVersions = new HashMap<>();
        File file = new File(basePath + File.separator + "map.JSON");
        try {
            if (file.exists()) {
                String json = FileUtils.readFileToString(file, "UTF-8");
                Type listType = new TypeToken<ArrayList<JSONMapper>>() {
                }.getType();
                List<JSONMapper> mapperList = new Gson().fromJson(json, listType);
                for (JSONMapper mapEntry : mapperList) {
                    toolsToVersions.put(mapEntry.toolname(), mapEntry.versions());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not load map.JSON");
        }

        return toolsToVersions;
    }

    //-----------------------HTML report generation-----------------------
    static String generateToolReport(final List<VersionDetail> versionsDetails) {
        List<VersionDetail> report = Lists.reverse(versionsDetails);

        StringBuilder fileReportBuilder = new StringBuilder();
        fileReportBuilder.append(STYLE);

        fileReportBuilder.append("<tr><th>Version</th><th>Meta-Version (API)</th><th>Size (GB)</th><th>Recent Executions</th><th>Availability</th><th>File Path</th></tr>");

        for (VersionDetail row : report) {

            if (!row.isValid()) {
                fileReportBuilder.append("<tr class = 'err'><td>");
            } else {
                fileReportBuilder.append("<tr><td>");
            }

            fileReportBuilder.append(row.getVersion());

            fileReportBuilder.append("</td><td>");
            fileReportBuilder.append(row.getMetaVersion());

            fileReportBuilder.append("</td><td>");
            if (row.getFileSize() != 0) {
                fileReportBuilder.append(bToGB(row.getFileSize()));
            }

            fileReportBuilder.append("</td><td>");
            List<String> times = row.getTimesOfExecution();
            Collections.sort(times, Comparator.comparing(FormattedTimeGenerator::strToDate));
            int timesSize = times.size();
            if (timesSize > 2) {
                fileReportBuilder.append(times.subList(timesSize - 2, timesSize));
            } else {
                fileReportBuilder.append(times);
            }

            fileReportBuilder.append("</td><td>");
            fileReportBuilder.append(row.isValid());

            fileReportBuilder.append("</td><td>");
            if (!"".equals(row.getPath())) {
                fileReportBuilder.append(row.getPath());
            }

            fileReportBuilder.append("</td></tr>");
        }
        fileReportBuilder.append("</table></body></html>");

        Document toolReport = Jsoup.parse(fileReportBuilder.toString());
        return toolReport.toString();
    }

    static String generateMainMenu(Set<String> tools, long addedInB, long totalInB) {
        StringBuilder menuBuilder = new StringBuilder();
        menuBuilder.append(STYLE);

        menuBuilder.append("<h1>Dockstore Back-Ups</h1><div>Added to Cloud: ");
        menuBuilder.append(bToGB(addedInB));
        menuBuilder.append(" GB</div>");
        menuBuilder.append("<div>Previously on Cloud: ");
        menuBuilder.append(bToGB(totalInB));
        menuBuilder.append(" GB</div>");

        for (String tool : tools) {
            menuBuilder.append("<tr><td><a href='");
            menuBuilder.append(tool);
            menuBuilder.append(".html'>");
            menuBuilder.append(tool);
            menuBuilder.append("</a></td></tr>");
        }
        menuBuilder.append("</table></body></html>");

        Document mainMenu = Jsoup.parse(menuBuilder.toString());
        return mainMenu.toString();
    }

    private record JSONMapper(String toolname, List<VersionDetail> versions) {

    }
}
