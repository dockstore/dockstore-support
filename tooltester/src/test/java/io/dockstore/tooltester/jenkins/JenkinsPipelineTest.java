package io.dockstore.tooltester.jenkins;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import com.google.gson.Gson;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author gluu
 * @since 21/02/17
 */
public class JenkinsPipelineTest {
    @Test
    public void testJson(){
        String json = "{\"_links\":{\"self\":{\"href\":\"/job/PipelineTest-quay.io-briandoconnor-dockstore-tool-md5sum-1.0.0/1/wfapi/describe\"},\"artifacts\":{\"href\":\"/job/PipelineTest-quay.io-briandoconnor-dockstore-tool-md5sum-1.0.0/1/wfapi/artifacts\"}},\"id\":\"1\",\"name\":\"#1\",\"status\":\"SUCCESS\",\"startTimeMillis\":1487697575126,\"endTimeMillis\":0,\"durationMillis\":0,\"queueDurationMillis\":179,\"pauseDurationMillis\":0,\"stages\":[{\"_links\":{\"self\":{\"href\":\"/job/PipelineTest-quay.io-briandoconnor-dockstore-tool-md5sum-1.0.0/1/execution/node/8/wfapi/describe\"}},\"id\":\"8\",\"name\":\"Build Dockerfile\",\"execNode\":\"\",\"status\":\"SUCCESS\",\"startTimeMillis\":1487697575305,\"durationMillis\":1876,\"pauseDurationMillis\":0},{\"_links\":{\"self\":{\"href\":\"/job/PipelineTest-quay.io-briandoconnor-dockstore-tool-md5sum-1.0.0/1/execution/node/10/wfapi/describe\"}},\"id\":\"10\",\"name\":\"Test test.json\",\"execNode\":\"\",\"status\":\"SUCCESS\",\"startTimeMillis\":1487697575308,\"durationMillis\":20987,\"pauseDurationMillis\":0}]}";
        Gson gson = new Gson();
        JenkinsPipeline jenkinsPipeline = gson.fromJson(json, JenkinsPipeline.class);
        assertTrue(jenkinsPipeline.getId().equals("1"));
        assertTrue(jenkinsPipeline.getName().equals("#1"));
        assertTrue(jenkinsPipeline.getStatus().equals("SUCCESS"));
        assertTrue(jenkinsPipeline.getStartTimeMillis()==1487697575126L);
        assertTrue(jenkinsPipeline.getEndTimeMillis()==0L);
        assertTrue(jenkinsPipeline.getDurationMillis()==0L);
        assertTrue(jenkinsPipeline.getQueueDurationMillis()==179L);
        assertTrue(jenkinsPipeline.getPauseDurationMillis()==0L);
    }

}