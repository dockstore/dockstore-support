package io.dockstore.tooltester.client.cli;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gluu
 * @since 26/01/17
 */
class ExceptionHandler {
    static final int GENERIC_ERROR = 1; // General error, not yet described by an error type
    static final int CONNECTION_ERROR = 150; // Connection exception
    static final int IO_ERROR = 3; // IO throws an exception
    static final int API_ERROR = 6; // API throws an exception
    static final int CLIENT_ERROR = 4; // Client does something wrong (ex. input validation)
    static final int COMMAND_ERROR = 10; // Command is not successful, but not due to errors
    static final AtomicBoolean DEBUG = new AtomicBoolean(false);
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandler.class);

    static void errorMessage(String message, int exitCode) {
        err(message);
        System.exit(exitCode);
    }

    private static void err(String format, Object... args) {
        LOG.error(String.format(format, args));
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
