package io.carbongate.cli;

import io.carbongate.policy.PolicyProfile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

record CliOptions(PolicyProfile profile, Path workspace, int port, List<String> trailing) {
    static CliOptions parse(String[] args) {
        PolicyProfile profile = PolicyProfile.BALANCED;
        Path workspace = Path.of(".").toAbsolutePath().normalize();
        int port = 8765;
        List<String> trailing = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--profile" -> profile = PolicyProfile.parse(required(args, ++i, "--profile"));
                case "--workspace" -> workspace = Path.of(required(args, ++i, "--workspace")).toAbsolutePath().normalize();
                case "--port" -> port = Integer.parseInt(required(args, ++i, "--port"));
                case "--" -> {
                    trailing.addAll(Arrays.asList(args).subList(i + 1, args.length));
                    i = args.length;
                }
                default -> trailing.add(args[i]);
            }
        }
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Port must be between 0 and 65535");
        return new CliOptions(profile, workspace, port, List.copyOf(trailing));
    }

    private static String required(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException(option + " requires a value");
        return args[index];
    }
}
