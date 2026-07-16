package io.carbongate.cli;

import io.carbongate.audit.AuditLog;
import io.carbongate.gateway.CarbonGateway;
import io.carbongate.json.Json;
import io.carbongate.mcp.McpProxy;
import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.model.Evaluation;
import io.carbongate.policy.PolicyEngine;
import io.carbongate.security.SecretScanner;

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class CarbonCli {
    private CarbonCli() {}

    public static void main(String[] args) {
        int exit;
        try {
            exit = execute(args);
        } catch (IllegalArgumentException e) {
            System.err.println("carbon: " + e.getMessage());
            exit = 2;
        } catch (Exception e) {
            System.err.println("carbon: " + e.getMessage());
            exit = 1;
        }
        if (exit != 0) System.exit(exit);
    }

    static int execute(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help") || args[0].equals("-h")) {
            usage();
            return 0;
        }
        return switch (args[0]) {
            case "check" -> check(slice(args, 1));
            case "exec" -> exec(slice(args, 1));
            case "gateway" -> gateway(slice(args, 1));
            case "mcp" -> mcp(slice(args, 1));
            case "redact" -> redact(slice(args, 1));
            case "run" -> run(slice(args, 1));
            case "version", "--version" -> version();
            default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
        };
    }

    private static int check(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        String command = requireCommand(options.trailing());
        Action action = Action.shell(command, options.workspace());
        Evaluation evaluation = new PolicyEngine(options.profile()).evaluate(action);
        audit(options.workspace()).append(action, evaluation);
        System.out.println(Json.stringify(evaluationMap(evaluation)));
        return evaluation.decision() == Decision.DENY ? 3 : 0;
    }

    private static int exec(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        Files.createDirectories(options.workspace());
        String command = requireCommand(options.trailing());
        Action action = Action.shell(command, options.workspace());
        Evaluation evaluation = new PolicyEngine(options.profile()).evaluate(action);
        audit(options.workspace()).append(action, evaluation);
        printEvaluation(evaluation);
        if (evaluation.decision() == Decision.DENY) return 3;
        if (evaluation.decision() == Decision.ASK && !authorize(command)) {
            System.err.println("Denied by user or non-interactive policy.");
            return 4;
        }
        ProcessBuilder builder = new ProcessBuilder(shell(command))
                .directory(options.workspace().toFile())
                .inheritIO();
        builder.environment().put("CARBON_WORKSPACE", options.workspace().toString());
        return builder.start().waitFor();
    }

    private static int gateway(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        Files.createDirectories(options.workspace());
        CarbonGateway gateway = new CarbonGateway(options.port(), options.workspace(),
                new PolicyEngine(options.profile()), audit(options.workspace()));
        Runtime.getRuntime().addShutdownHook(new Thread(gateway::close));
        gateway.start();
        System.err.printf("CarbonGate listening on http://127.0.0.1:%d (profile=%s, workspace=%s)%n",
                gateway.port(), options.profile().name().toLowerCase(), options.workspace());
        new CountDownLatch(1).await();
        return 0;
    }

    private static int mcp(String[] args) throws Exception {
        if (args.length == 0 || !args[0].equals("proxy")) {
            throw new IllegalArgumentException("Usage: carbon mcp proxy [options] -- SERVER [ARGS...]");
        }
        CliOptions options = CliOptions.parse(slice(args, 1));
        if (options.trailing().isEmpty()) throw new IllegalArgumentException("MCP server command is required");
        Files.createDirectories(options.workspace());
        return new McpProxy(options.workspace(), new PolicyEngine(options.profile()), audit(options.workspace()))
                .run(options.trailing());
    }

    private static int redact(String[] args) {
        if (args.length == 0) throw new IllegalArgumentException("Text to redact is required");
        var result = new SecretScanner().scan(String.join(" ", args));
        System.out.println(result.redacted());
        if (result.containsSecrets()) System.err.println("Redacted: " + String.join(", ", result.findings()));
        return 0;
    }

    private static int run(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        if (options.trailing().isEmpty()) throw new IllegalArgumentException("Agent command is required");
        Files.createDirectories(options.workspace());
        System.err.println("CarbonGate integration launcher: this mode is not an OS sandbox.");
        try (CarbonGateway gateway = new CarbonGateway(options.port(), options.workspace(),
                new PolicyEngine(options.profile()), audit(options.workspace()))) {
            gateway.start();
            ProcessBuilder builder = new ProcessBuilder(options.trailing())
                    .directory(options.workspace().toFile())
                    .inheritIO();
            builder.environment().put("CARBON_ENDPOINT", "http://127.0.0.1:" + gateway.port());
            builder.environment().put("CARBON_WORKSPACE", options.workspace().toString());
            builder.environment().put("CARBON_PROFILE", options.profile().name().toLowerCase());
            return builder.start().waitFor();
        }
    }

    private static int version() {
        System.out.println("CarbonGate 0.1.0 (Java 21)");
        return 0;
    }

    private static boolean authorize(String command) {
        if ("allow".equalsIgnoreCase(System.getenv("CARBON_NON_INTERACTIVE"))) return true;
        Console console = System.console();
        if (console == null) return false;
        String answer = console.readLine("Allow once? [y/N] %s%n> ", command);
        return answer != null && (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes"));
    }

    private static List<String> shell(String command) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return List.of("cmd.exe", "/d", "/s", "/c", command);
        return List.of("/bin/sh", "-lc", command);
    }

    private static String requireCommand(List<String> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("Shell command is required after --");
        return String.join(" ", values);
    }

    private static AuditLog audit(Path workspace) {
        return new AuditLog(workspace.resolve(".carbongate/audit.jsonl"));
    }

    private static void printEvaluation(Evaluation evaluation) {
        System.err.printf("CarbonGate: %s (%s) — %s%n", evaluation.decision().name().toLowerCase(),
                evaluation.risk().name().toLowerCase(), evaluation.reason());
        evaluation.findings().forEach(finding -> System.err.println("  - " + finding));
    }

    private static Map<String, Object> evaluationMap(Evaluation evaluation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", evaluation.id());
        result.put("decision", evaluation.decision().name().toLowerCase());
        result.put("risk", evaluation.risk().name().toLowerCase());
        result.put("reason", evaluation.reason());
        result.put("findings", evaluation.findings());
        result.put("sanitizedResource", evaluation.sanitizedResource());
        return result;
    }

    private static String[] slice(String[] values, int from) {
        return java.util.Arrays.copyOfRange(values, from, values.length);
    }

    private static void usage() {
        System.out.println("""
                CarbonGate — zero-trust runtime gateway for AI agents

                Usage:
                  carbon check [--profile PROFILE] [--workspace PATH] -- COMMAND
                  carbon exec [--profile PROFILE] [--workspace PATH] -- COMMAND
                  carbon gateway [--profile PROFILE] [--workspace PATH] [--port 8765]
                  carbon mcp proxy [--profile PROFILE] [--workspace PATH] -- SERVER [ARGS...]
                  carbon redact TEXT
                  carbon run [--workspace PATH] -- AGENT [ARGS...]
                  carbon version
                """);
    }
}
