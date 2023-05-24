package io.dockstore.toolbackup.client.cli.common;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.USER_HOME;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

/**
 * Created by kcao on 08/02/17.
 */
public final class AWSConfig {
    private AWSConfig() {
        // hidden constructor
    }
    public static void generateCredentials() {
        File awsCredentials = new File(USER_HOME + File.separator + ".aws/credentials");
        StringBuilder stringBuilder = new StringBuilder();
        if (!awsCredentials.exists()) {
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
