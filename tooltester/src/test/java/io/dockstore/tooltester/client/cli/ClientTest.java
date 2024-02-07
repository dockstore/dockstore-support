package io.dockstore.tooltester.client.cli;

import static io.dockstore.tooltester.client.cli.Client.main;
import static io.dockstore.utils.ExceptionHandler.COMMAND_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Many tests ignored due to reasons explained in this PR https://github.com/dockstore/dockstore-support/pull/448
 */

@ExtendWith(SystemStubsExtension.class)
public class ClientTest {

    @SystemStub
    private SystemOut systemOut;

    @SystemStub
    private SystemErr systemErr;

    /**
     * Test with unknown command
     */
    @Test
    public void unknownCommand() throws Exception {
        String[] argv = {"mmmrrrggglll"};
        int exitCode = catchSystemExit(() -> {
            main(argv);
        });
        assertEquals(COMMAND_ERROR, exitCode);
    }

    /**
     * This tests the client with no parameters
     */
    @Test
    public void emptyOne() throws InterruptedException {
        String[] argv = {""};
        main(argv);
        assertTrue(systemOut.getText().contains("Usage: autotool [options] [command] [command options]"));
    }

    @Test
    public void emptyTwo() throws InterruptedException {
        String[] argv = {};
        main(argv);
        assertTrue(systemOut.getText().contains("Usage: autotool [options] [command] [command options]"));
    }

    /**
     * This displays the help menu for the run-workflows-through-wes command
     */
    @Test
    public void runWorkflowHelp() throws InterruptedException {
        String[] argv = {"run-workflows-through-wes", "--help"};
        main(argv);
        assertTrue(systemOut.getText().contains("Runs workflows through the Dockstore CLI and AGC, then both prints and uploads to Dockstore the execution statistics."));
    }

    /**
     * This displays the help menu for the upload-results command
     */
    @Test
    public void runUploadResultsHelp() throws InterruptedException {
        String[] argv = {"upload-results", "--help"};
        main(argv);
        assertTrue(systemOut.getText().contains("Uploads run results from the `run-workflows-through-wes` command to a specified dockstore site."));
    }

    /**
     * This displays the help menu for the main command
     */
    @Test
    public void mainHelp() throws InterruptedException {
        String[] argv = {"--help"};
        main(argv);
        assertTrue(systemOut.getText().contains("Usage: autotool [options] [command] [command options]"));
    }
}
