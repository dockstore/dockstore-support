package io.dockstore.tooltester.client.cli;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author gluu
 * @since 26/01/17
 */
public class ExceptionHandler {
    public static final int GENERIC_ERROR = 1; // General error, not yet described by an error type
    public static final int CONNECTION_ERROR = 150; // Connection exception
    public static final int IO_ERROR = 3; // IO throws an exception
    public static final int API_ERROR = 6; // API throws an exception
    public static final int CLIENT_ERROR = 4; // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10; // Command is not successful, but not due to errors
    public static final AtomicBoolean DEBUG = new AtomicBoolean(false);

    public static void errorMessage(String message, int exitCode) {
        err(message);
        System.exit(exitCode);
    }

    private static void err(String format, Object... args) {
        System.err.println(String.format(format, args));
    }

    static void exceptionMessage(Exception exception, String message, int exitCode) {
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
