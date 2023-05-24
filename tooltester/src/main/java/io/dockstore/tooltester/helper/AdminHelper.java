package io.dockstore.tooltester.helper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Job;

import static io.dockstore.tooltester.helper.ExceptionHandler.IO_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;

/**
 * @author gluu
 * @since 27/03/17
 */
public class AdminHelper {
    /**
     * This function deletes all jobs on jenkins matching "Test.*"
     * Only used by admin
     */
    public void deleteJobs(String pattern) {
        try {
            JenkinsServer jenkinsServer = null;
            try {
                jenkinsServer = new JenkinsServer(new URI("http://172.18.0.22:8080"), "username", "password");
            } catch (URISyntaxException e) {
                e.printStackTrace();
                throw new RuntimeException("Could not get Jenkins server");
            }
            Map<String, Job> jobs = jenkinsServer.getJobs();
            JenkinsServer finalJenkinsServer = jenkinsServer;
            jobs.entrySet().stream().filter(map -> map.getKey().matches(pattern + ".+")).forEach(map -> {
                try {

                    finalJenkinsServer.deleteJob(map.getKey(), true);
                } catch (IOException e) {
                    exceptionMessage(e, "Could not delete Jenkins job", IO_ERROR);
                }
            });
        } catch (IOException e) {
            exceptionMessage(e, "Could not find and delete Jenkins job", IO_ERROR);
        }
    }
}
