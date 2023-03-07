package io.dockstore.tooltester.helper;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gluu
 * @since 26/01/17
 */
public class ExceptionHandler {
    public static final int GENERIC_ERROR = 1; // General error, not yet described by an error type
    public static final int IO_ERROR = 3; // IO throws an exception
    public static final int API_ERROR = 6; // API throws an exception
    public static final int CLIENT_ERROR = 4; // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10; // Command is not successful, but not due to errors
    public static final AtomicBoolean DEBUG = new AtomicBoolean(false);
    static final int CONNECTION_ERROR = 150; // Connection exception
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandler.class);

    private ExceptionHandler() {

    }

    public static void errorMessage(String message, int exitCode) {
        err(message);
        System.exit(exitCode);
    }

    private static void err(String format, Object... args) {
        LOG.error(String.format(format, args));
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
