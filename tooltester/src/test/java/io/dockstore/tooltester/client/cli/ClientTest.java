package io.dockstore.tooltester.client.cli;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.junit.Assert;
import org.junit.Test;

public class ClientTest {
    @Test
    public void setupEnvironment() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);
        client.setupClientEnvironment();
        Assert.assertTrue("client API could not start", client.getContainersApi() != null);
    }

}