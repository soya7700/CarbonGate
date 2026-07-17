package io.carbongate.enterprise.cli;

import io.carbongate.enterprise.component.ComponentManager;
import io.carbongate.enterprise.component.ComponentPackageBuilder;
import io.carbongate.json.Json;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

public final class EnterpriseCli {
    private EnterpriseCli() {}

    public static void main(String[] args) {
        int exit;
        try {
            exit = execute(args);
        } catch (IllegalArgumentException error) {
            System.err.println("carbon-enterprise: " + error.getMessage());
            exit = 2;
        } catch (Exception error) {
            System.err.println("carbon-enterprise: " + compact(error));
            exit = 1;
        }
        if (exit != 0) System.exit(exit);
    }

    static int execute(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
            usage();
            return 0;
        }
        ComponentManager components = new ComponentManager(carbonHome());
        return switch (args[0]) {
            case "install" -> {
                requireLength(args, 2);
                System.out.println(Json.stringify(components.install(Path.of(args[1]))));
                yield 0;
            }
            case "package" -> {
                requireLength(args, 3);
                var manifest = new ComponentPackageBuilder().build(Path.of(args[1]), Path.of(args[2]));
                System.out.println(Json.stringify(Map.of("state", "packaged", "component", manifest.map(),
                        "archive", Path.of(args[2]).toAbsolutePath().normalize().toString())));
                yield 0;
            }
            case "list" -> {
                requireLength(args, 1);
                System.out.println(Json.stringify(Map.of("components", components.list())));
                yield 0;
            }
            case "enable", "rollback" -> {
                requireLength(args, 3);
                System.out.println(Json.stringify(components.enable(args[1], args[2])));
                yield 0;
            }
            case "disable" -> {
                requireLength(args, 2);
                System.out.println(Json.stringify(components.disable(args[1])));
                yield 0;
            }
            case "remove" -> {
                requireLength(args, 3);
                System.out.println(Json.stringify(components.remove(args[1], args[2])));
                yield 0;
            }
            case "doctor" -> {
                requireLength(args, 1);
                Map<String, Object> result = components.doctor();
                System.out.println(Json.stringify(result));
                yield Boolean.TRUE.equals(result.get("healthy")) ? 0 : 6;
            }
            case "invoke" -> {
                requireLength(args, 4);
                System.out.println(Json.stringify(components.invoke(args[1], args[2], Json.object(args[3]))));
                yield 0;
            }
            case "guard" -> {
                requireLength(args, 2);
                System.out.println(Json.stringify(components.guard(Json.object(args[1]))));
                yield 0;
            }
            case "version", "--version" -> {
                System.out.println("CarbonGate Enterprise Component Host 0.1.0 (protocol v1, Java 21)");
                yield 0;
            }
            default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
        };
    }

    private static Path carbonHome() {
        String configured = System.getenv("CARBON_HOME");
        return Path.of(configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".carbongate").toString() : configured)
                .toAbsolutePath().normalize();
    }

    private static void requireLength(String[] args, int length) {
        if (args.length != length) throw new IllegalArgumentException("Invalid arguments: " + Arrays.toString(args));
    }

    private static void usage() {
        System.out.println("""
                CarbonGate Enterprise Component Host

                  carbon-enterprise install COMPONENT.carbon
                  carbon-enterprise package SOURCE_DIRECTORY OUTPUT.carbon
                  carbon-enterprise list
                  carbon-enterprise enable ID VERSION
                  carbon-enterprise rollback ID VERSION
                  carbon-enterprise disable ID
                  carbon-enterprise remove ID VERSION
                  carbon-enterprise doctor
                  carbon-enterprise invoke ID OPERATION JSON
                  carbon-enterprise guard JSON
                  carbon-enterprise version
                """);
    }

    private static String compact(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.substring(0, Math.min(message.length(), 256));
    }
}
