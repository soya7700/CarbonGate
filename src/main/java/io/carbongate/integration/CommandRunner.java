package io.carbongate.integration;

import java.io.IOException;
import java.util.List;

public interface CommandRunner {
    boolean available(String executable);

    Result run(List<String> command) throws IOException, InterruptedException;

    record Result(int exitCode, String output, boolean timedOut) {
        public boolean succeeded() {
            return exitCode == 0 && !timedOut;
        }
    }
}
