package io.dockstore.toolbackup.client.cli;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by kcao on 12/01/17.
 */
public class ErrorExit {
    static final AtomicBoolean DEBUG = new AtomicBoolean(false);
    private static void err(String format, Object... args) {
        System.err.println(String.format(format, args));
    }
    public static void errorMessage(String message, int exitCode) {
        err(message);
        System.exit(exitCode);
    }

     public static void exceptionMessage(Exception exception, String message, int exitCode) {
        if (!message.equals("")) {
            err(message);
        }
        if (DEBUG.get()) {
            exception.printStackTrace();
        } else {
            err(exception.toString());
        }
        System.exit(exitCode);
    }
}
