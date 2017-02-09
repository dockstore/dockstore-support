package io.dockstore.toolbackup.client.cli.common;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.USER_HOME;

/**
 * Created by kcao on 08/02/17.
 */
public class AWSConfig {
    public static void generateCredentials() {
        File awsCredentials = new File(USER_HOME + File.separator + ".aws/credentials");
        StringBuilder stringBuilder = new StringBuilder();
        if(!awsCredentials.exists()) {
            stringBuilder.append("[default]\n");
            stringBuilder.append("aws_access_key_id=MOCK_ACCESS_KEY\n");
            stringBuilder.append("aws_secret_access_key=MOCK_SECRET_KEY\n");

            try {
                FileUtils.writeStringToFile(awsCredentials, stringBuilder.toString(), "UTF-8");
            } catch (IOException e) {
                throw new RuntimeException("Could not create ~/.aws/credentials");
            }
        }
    }
}
