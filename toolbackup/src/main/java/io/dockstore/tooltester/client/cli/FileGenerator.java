package io.dockstore.tooltester.client.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by kcao on 11/01/17.
 */
class FileGenerator {
    static File inputStreamToFile(InputStream inputStream, String filePath) {
        OutputStream outputStream = null;
        File outputFile = null;
        int read = 0;
        byte[] bytes;
        final int bufferSize = 1024;
        try {
            Path fPath = Paths.get(filePath);
            outputFile = new File(filePath);

            if (!Files.exists(fPath)) {
                outputStream = new FileOutputStream(outputFile);

                bytes = new byte[bufferSize];

                while ((read = inputStream.read(bytes)) != -1) {
                    outputStream.write(bytes, 0, read);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    // outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return outputFile;
    }
}
