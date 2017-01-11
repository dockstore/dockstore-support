package io.dockstore.tooltester.client.cli;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleFunction;

/**
 * Created by kcao on 11/01/17.
 */
class ReportGenerator {
    private static final String STYLE = "<html><head><style>table { font-family: arial, sans-serif; border-collapse: collapse; width: 100%; }"
            + "td, th { border: 1px solid #dddddd; text-align: left; padding: 8px; }"
            + "tr:nth-child(even) { background-color: #dddddd; }"
            + "</style></head>";
    private static final double NUM_B_IN_GB = 1e9;
    private static final double DOUBLE_SPECIAL_NUM = 100.0;
    private static DoubleFunction<Double> bytesToGB = (bytes) -> bytes/NUM_B_IN_GB;
    private static DoubleFunction<Double> format2DecPlaces = (num) -> Math.round(num*DOUBLE_SPECIAL_NUM)/DOUBLE_SPECIAL_NUM;
    private static String sizeToStr(double sizeInB) {
        return Double.toString(format2DecPlaces.apply(bytesToGB.apply(sizeInB)));
    }

    static String generateFilesReport(List<VersionDetail> listOfVersionDetails) {
        String generated = "<tr><th>Version</th><th>Date</th><th>Size (GB)</th><th>Creation Time</th></tr>";
        for(VersionDetail row : listOfVersionDetails) {
            generated += "<tr><td>" + row.getVersion() + "</td><td>" + row.getDate() + "</td><td>" + sizeToStr(row.getSize()) + "</td><td>" + row.getCreationTime() + "</td></tr>";
        }
        return STYLE + "<body><table>" + generated + "</table></body></html>";
    }

    static String generateMenu(Set<String> listOfToolNames, double totalSizeInB) {
        String generated = "<h1>Dockstore Back-Ups</h1><div>Total Size: " + sizeToStr(totalSizeInB) + " GB</div>";
        for(String tool : listOfToolNames) {
            generated += "<tr><td><a href='" + tool + ".html'>" + tool + "</a></td></tr>";
        }
        return STYLE + "<body><table>" + generated + "</table></body></html>";
    }

    static String generateJSONMap(Map<String, List<VersionDetail>> toolNameToList) {
        String jsonMap = "[\n";
        int toolCount = 0;
        for (Map.Entry<String, List<VersionDetail>> entry : toolNameToList.entrySet()) {
            jsonMap += "{\n'toolname': '" + entry.getKey() + "',\n" + "'versions': [\n";

            int versionCount = 0;
            List<VersionDetail> listOfVersions = entry.getValue();
            for(VersionDetail row: listOfVersions) {
                jsonMap += "{\n'version': '" + row.getVersion() + "',\n"
                        + "'date': '" + row.getDate() + "',\n"
                        + "'size': '" + row.getSize() + "',\n"
                        + "'creation-time': '" + row.getCreationTime();
                versionCount++;
                if(versionCount == listOfVersions.size()) {
                    jsonMap += "'\n}\n";
                } else {
                    jsonMap += "'\n},\n";
                }
            }
            toolCount++;
            if(toolCount == toolNameToList.size()) {
                jsonMap += "]\n}\n";
            } else {
                jsonMap += "]\n},\n";
            }

        }
        jsonMap += "]";
        return jsonMap;
    }
}
