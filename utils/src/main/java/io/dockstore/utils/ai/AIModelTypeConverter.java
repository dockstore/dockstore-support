package io.dockstore.utils.ai;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * JCommander converter that parses a string into an {@link AIModelType}.
 */
public class AIModelTypeConverter implements IStringConverter<AIModelType> {
    @Override
    public AIModelType convert(String value) {
        try {
            return ClaudeModelType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            String validValues = Arrays.stream(ClaudeModelType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
            throw new ParameterException("Invalid AI model '" + value + "'. Valid values: " + validValues);
        }
    }
}
