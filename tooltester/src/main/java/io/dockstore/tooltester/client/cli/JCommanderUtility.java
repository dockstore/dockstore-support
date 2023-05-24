/*
 *    Copyright 2023 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.tooltester.client.cli;

import com.beust.jcommander.DefaultUsageFormatter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.Strings;
import com.beust.jcommander.WrappedParameter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This is very similar to
 * https://github.com/dockstore/dockstore-cli/blob/ca3c73f8884ec2a9cc5b25e6a3b26594df1752f1/dockstore-client/src/main/java/io/dockstore/client/cli/JCommanderUtility.java
 */
public final class JCommanderUtility {
    private JCommanderUtility() {
        // hide the constructor for utility classes
    }

    public static void out(String arg) {
        System.out.println(arg);
    }

    public static void outFormatted(String format, Object... args) {
        System.out.println(String.format(format, args));
    }


    public static void printJCommanderHelp(JCommander jc, String programName, String commandName) {
        JCommander commander = jc.getCommands().get(commandName);
        DefaultUsageFormatter formatter = new DefaultUsageFormatter(jc);
        String description = formatter.getCommandDescription(commandName);
        List<ParameterDescription> sorted = commander.getParameters();

        printJCommanderHelpUsage(programName, commandName, commander);
        printJCommanderHelpDescription(description);
        printJCommanderHelpCommand(commander);
        printJCommanderHelpRequiredParameters(sorted);
        printJCommanderHelpOptionalParameters(sorted);
    }

    private static void printJCommanderHelpUsage(String programName, String commandName, JCommander jc) {
        out("Usage: " + programName + " " + commandName + " --help");
        out("       " + programName + " " + commandName + " [parameters] [command]");
        out("");
    }

    private static void printJCommanderHelpCommand(JCommander jc) {
        DefaultUsageFormatter formatter = new DefaultUsageFormatter(jc);
        Map<String, JCommander> commands = jc.getCommands();
        if (!commands.isEmpty()) {
            out("Commands: ");
            for (Map.Entry<String, JCommander> commanderEntry : commands.entrySet()) {
                out("  " + commanderEntry.getKey());
                out("    " + formatter.getCommandDescription(commanderEntry.getKey()));
            }
        }
    }

    private static void printJCommanderHelpDescription(String commandDescription) {
        out("Description:");
        out("  " + commandDescription);
        out("");
    }

    private static void printJCommanderHelpRequiredParameters(List<ParameterDescription> sorted) {
        int maxLength = 0;
        for (ParameterDescription pd : sorted) {
            int length = pd.getNames().length();
            maxLength = Math.max(length, maxLength);
        }
        maxLength = ((maxLength + 2) * 2);
        boolean first = true;
        for (ParameterDescription pd : sorted) {
            WrappedParameter parameter = pd.getParameter();
            if (parameter.required() && !Objects.equals(pd.getNames(), "--help")) {
                if (first) {
                    out("Required parameters:");
                    first = false;
                }
                printJCommanderHelpParameter(pd, parameter, maxLength);
            }
        }
        out("");
    }

    private static void printJCommanderHelpOptionalParameters(List<ParameterDescription> sorted) {
        int maxLength;
        ParameterDescription maxParameter = Collections.max(sorted, (first, second) -> {
            if (first.getNames().length() > second.getNames().length()) {
                return 1;
            } else {
                return 0;
            }
        });
        maxLength = ((maxParameter.getNames().length() + 2) * 2);
        boolean first = true;
        for (ParameterDescription pd : sorted) {
            WrappedParameter parameter = pd.getParameter();
            if (!parameter.required() && !pd.getNames().equals("--help")) {
                if (first) {
                    out("Optional parameters:");
                    first = false;
                }
                printJCommanderHelpParameter(pd, parameter, maxLength);
            }
        }
        out("");
    }

    private static void printJCommanderHelpParameter(ParameterDescription pd, WrappedParameter parameter, int maxLength) {
        outFormatted("%-" + maxLength + "s %s", "  " + pd.getNames() + " <" + pd.getNames().replaceAll("--", "") + ">", pd.getDescription());
        Object def = pd.getDefault();
        if (def != null && !pd.isHelp()) {
            String displayedDef = Strings.isStringEmpty(def.toString()) ? "<empty string>" : def.toString();
            outFormatted("%-" + maxLength + "s %s", "  ", "Default: " + (parameter.password() ? "********" : displayedDef));
        }
    }

}
