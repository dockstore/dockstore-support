package io.dockstore.tooltester.client.cli;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleFunction;

/**
 * Created by kcao on 11/01/17.
 */
class ReportGenerator {
    private static final String STYLE =
            "<html><head><style>.err { color: #ff0000; }"
            + "table { font-family: arial, sans-serif; border-collapse: collapse; width: 100%; }"
            + "td, th { border: 1px solid #dddddd; text-align: left; padding: 8px; }"
            + "</style></head><body><table>";
    private static final double NUM_B_IN_GB = 1e9;
    private static final double DOUBLE_SPECIAL_NUM = 100.0;

    private static double bToGB(double sizeInB) {
        DoubleFunction<Double> bytesToGB = (bytes) -> bytes/NUM_B_IN_GB;
        DoubleFunction<Double> format2DecPlaces = (num) -> Math.round(num*DOUBLE_SPECIAL_NUM)/DOUBLE_SPECIAL_NUM;
        return format2DecPlaces.apply(bytesToGB.apply(sizeInB));
    }

    private static class JSONMapper {
        final String toolname;
        final List<VersionDetail> versions;
        JSONMapper(String toolname, List<VersionDetail> versions) {
            this.toolname = toolname;
            this.versions = versions;
        }

        public String getToolname() {
            return toolname;
        }
        public List<VersionDetail> getVersions() {
            return versions;
        }
    }

    static String generateFilesReport(List<VersionDetail> listOfVersionDetails) {
        StringBuilder fileReportBuilder = new StringBuilder();
        fileReportBuilder.append(STYLE);

        fileReportBuilder.append("<tr><th>Version</th><th>Meta-Version</th><th>Size (GB)</th><th>Creation Time</th><th>Availability</th><th>Time of Execution</th></tr>");

        for(VersionDetail row : listOfVersionDetails) {

            if(!row.isValid()) {
                fileReportBuilder.append("<tr class = 'err'><td>");
            } else {
                fileReportBuilder.append("<tr><td>");
            }

            fileReportBuilder.append(row.getVersion());

            fileReportBuilder.append("</td><td>");
            fileReportBuilder.append(row.getMetaVersion());

            fileReportBuilder.append("</td><td>");
            if(row.getSize() != 0) {
                fileReportBuilder.append(bToGB(row.getSize()));
            }

            fileReportBuilder.append("</td><td>");
            if(row.getCreationTime() != null) {
                fileReportBuilder.append(row.getCreationTime());
            }

            fileReportBuilder.append("</td><td>");
            fileReportBuilder.append(row.isValid());

            fileReportBuilder.append("</td><td>");
            fileReportBuilder.append(row.getScriptTime());

            fileReportBuilder.append("</td></tr>");
        }
        fileReportBuilder.append("</table></body></html>");

        return fileReportBuilder.toString();
    }

    static String generateMenu(Set<String> listOfToolNames, double totalSizeInB) {
        StringBuilder menuBuilder = new StringBuilder();
        menuBuilder.append(STYLE);

        menuBuilder.append("<h1>Dockstore Back-Ups</h1><div>Total Size: ");
        menuBuilder.append(bToGB(totalSizeInB));
        menuBuilder.append(" GB</div>");

        for(String tool : listOfToolNames) {
            menuBuilder.append("<tr><td><a href='");
            menuBuilder.append(tool);
            menuBuilder.append(".html'>");
            menuBuilder.append(tool);
            menuBuilder.append("</a></td></tr>");
        }
        menuBuilder.append("</table></body></html>");

        return menuBuilder.toString();
    }

    static void generateJSONMap(Map<String, List<VersionDetail>> toolNameToList, String basePath) {
        List<JSONMapper> mapperList = new ArrayList<>();
        for(Map.Entry<String, List<VersionDetail>> entry: toolNameToList.entrySet()) {
            JSONMapper jsonMapper = new JSONMapper(entry.getKey(), entry.getValue());
            mapperList.add(jsonMapper);
        }

        File file = new File(basePath+"map.JSON");
        Gson gson = new Gson();
        try {
            FileUtils.writeStringToFile(file, gson.toJson(mapperList), "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("Could not create or add to map.JSON");
        }
    }

    static Map<String, List<VersionDetail>> loadJSONMap(String basePath) {
        Map<String, List<VersionDetail>> toolNameToList = new HashMap<>();
        File file = new File(basePath+"map.JSON");
        try {
            if(file.exists()) {
                String json = null;
                json = FileUtils.readFileToString(file, "UTF-8");
                Type listType = new TypeToken<ArrayList<JSONMapper>>(){}.getType();
                List<JSONMapper> mapperList = new Gson().fromJson(json, listType);
                for(JSONMapper mapEntry: mapperList) {
                    toolNameToList.put(mapEntry.getToolname(), mapEntry.getVersions());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not load map.JSON");
        }
        return toolNameToList;
    }
}
