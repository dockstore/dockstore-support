package io.dockstore.githubdelivery;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.io.File;

public class GithubDeliveryCommandLineArgs {

    public static final String DEFAULT_CONFIG_FILE_NAME = "github-delivery.config";

    @Parameter(names = "--help", description = "Prints help for githubdelivery", help = true)
    private boolean help = false;

    @Parameter(names = {"-c", "--config"}, description = "The config file path.")
    private File config = new File("./" + DEFAULT_CONFIG_FILE_NAME);

    public boolean isHelp() {
        return help;
    }

    public File getConfig() {
        return config;
    }

    @Parameters(commandNames = { "download-event" }, commandDescription = "Download github event from S3 bucket using key.")
    public static class DownloadEventCommand {

        @Parameter(names = {"-k", "--key"}, description = "The key of the event in bucket. Format should be YYYY-MM-DD/deliveryid")
        private String bucketKey;

        @Parameter(names = {"-d", "--date"}, description = "All events from the date. Format should be YYYY-MM-DD")
        private String date;

        public String getBucketKey() {
            return bucketKey;
        }
    }
    @Parameters(commandNames = { "submit-events" }, commandDescription = "Submit all github events from S3 bucket from a specific date to the webservice.")
    public static class SubmitEventsCommand {

        @Parameter(names = {"-d", "--date"}, description = "All events from the date. Format should be YYYY-MM-DD")
        private String date;

        public String getDate() {
            return date;
        }
    }
}
