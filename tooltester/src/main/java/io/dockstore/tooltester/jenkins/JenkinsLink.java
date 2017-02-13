package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 08/02/17
 */
public class JenkinsLink {
    private Self self;
    private Self log;

    public Self getSelf() {
        return self;
    }

    public Self getLog() {
        return log;
    }

    public class Self {
        private String href;

        Self() {

        }

        public String getHref() {
            return href;
        }
    }
}
