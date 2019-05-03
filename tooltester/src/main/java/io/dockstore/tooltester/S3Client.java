package io.dockstore.tooltester;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.MediaType;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gluu
 * @since 18/04/19
 */
public class S3Client {
    private static final Logger LOG = LoggerFactory.getLogger(S3Client.class);
    private static final int MAX_TOOL_ID_STRING_SEGMENTS = 5;
    private static final int TOOL_ID_REPOSITORY_INDEX = 3;
    private static final int TOOL_ID_TOOLNAME_INDEX = 4;
    private String bucketName;
    private AmazonS3 s3;

    public S3Client() {
        TooltesterConfig tooltesterConfig = new TooltesterConfig();
        String s3Endpoint = tooltesterConfig.getS3Endpoint();
        bucketName = tooltesterConfig.getS3Bucket();
        AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(s3Endpoint,
                Regions.US_EAST_1.getName());
        s3 = AmazonS3ClientBuilder.standard().withEndpointConfiguration(endpointConfiguration).build();
    }

    /**
     * This essentially generates the filePath of the log that will be stored on S3
     *
     * @param toolId       The GA4GH Tool ID
     * @param versionName  The GA4GH ToolVersion name
     * @param testFilePath The file that was tested (Dockerfile, test.json, etc)
     * @param runner       The runner used to test (cwltool, cromwell, etc)
     * @param startTime    The start time in milliseconds since epoch
     * @return S3 key (file path)
     * @throws UnsupportedEncodingException Could not endpoint string
     */
    private static String generateKey(String toolId, String versionName, String testFilePath, String runner, String startTime)
            throws UnsupportedEncodingException {
        List<String> pathList = new ArrayList<>();
        pathList.add(convertToolIdToPartialKey(toolId));
        pathList.add(URLEncoder.encode(versionName, StandardCharsets.UTF_8.toString()));
        pathList.add(URLEncoder.encode(testFilePath, StandardCharsets.UTF_8.toString()));
        pathList.add(URLEncoder.encode(runner, StandardCharsets.UTF_8.toString()));
        pathList.add(URLEncoder.encode(startTime + ".log", StandardCharsets.UTF_8.toString()));
        return String.join("/", pathList);
    }

    /**
     * Converts the toolId into a key for s3 storage.  Used by both webservice and tooltester
     * Workflows will be in a "workflow" directory whereas tools will be in a "tool" directory
     * repository and optional toolname or workflowname must be encoded or else looking for logs of a specific tool without toolname (quay.io/dockstore/hello_world)
     * will return logs for the other ones with toolnames (quay.io/dockstore/hello_world/thing)
     *
     * @param toolId TRS tool ID    (ex. quay.io/pancancer/pcawg-bwa-mem-workflow/thing)
     * @return The key for s3       (ex. tool/quay.io/pancancer/pcawg-bwa-mem-workflow%2Fthing)
     */
    protected static String convertToolIdToPartialKey(String toolId) throws UnsupportedEncodingException {
        if (toolId.startsWith("#workflow")) {
            toolId = toolId.replaceFirst("#workflow", "workflow");
        } else {
            toolId = "tool/" + toolId;
        }
        String[] split = toolId.split("/");
        if (split.length == MAX_TOOL_ID_STRING_SEGMENTS) {
            split[TOOL_ID_REPOSITORY_INDEX] = URLEncoder
                    .encode(split[TOOL_ID_REPOSITORY_INDEX] + "/" + split[TOOL_ID_TOOLNAME_INDEX], StandardCharsets.UTF_8.toString());
            String[] encodedToolIdArray = Arrays.copyOf(split, split.length - 1);
            return String.join("/", encodedToolIdArray);
        } else {
            return toolId;
        }
    }

    /**
     * Create s3 object with metadata and upload it to s3
     * @param toolId    The TRS ToolId
     * @param versionName   The TRS ToolVersion name
     * @param buildName  The name of the build on Jenkins
     * @param runner    The runner used to test the file (cwltool, cromwell)
     * @param logContent    The contents of the log file from ToolTester
     * @param startTime     The start time (seconds since epoch) when the file was tested
     */
    public void createObject(String toolId, String versionName, String buildName, String runner, String logContent, String startTime) {
        try {
            String testFilePath = buildNameToTestFilePath(buildName);
            String key = generateKey(toolId, versionName, testFilePath, runner, startTime);
            byte[] contentAsBytes = logContent.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream contentsAsStream = new ByteArrayInputStream(contentAsBytes);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(MediaType.TEXT_PLAIN);
            metadata.addUserMetadata("tool_id", toolId);
            metadata.addUserMetadata("version_name", versionName);
            metadata.addUserMetadata("test_file_path", testFilePath);
            metadata.addUserMetadata("runner", runner);
            metadata.setContentLength(contentAsBytes.length);
            PutObjectRequest request = new PutObjectRequest(bucketName, key, contentsAsStream, metadata);
            s3.putObject(request);
        } catch (UnsupportedEncodingException e) {
            LOG.warn("Could not generate S3 URL: " + e.getMessage());
        }
    }

    /**
     * All Jenkins builds that tests a parameter file was named "Build ..." for readability.
     * All Jenkins builds that tests a Dockerfile was named "Test ..." for readability.
     * Stripping the beginning part for readability
     * @param buildName  The name of the Jenkins build ("Build Dockerfile", "Test test.json", etc)
     * @return  The file path only
     */
    private String buildNameToTestFilePath(String buildName) {
        return buildName.replaceFirst("^Build ", "").replaceFirst("^Test ", "");
    }
}
