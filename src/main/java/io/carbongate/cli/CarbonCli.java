package io.carbongate.cli;

import io.carbongate.audit.AuditSink;
import io.carbongate.audit.EnterpriseAuditLog;
import io.carbongate.audit.SecurityEventLog;
import io.carbongate.authorization.AuthorizationStore;
import io.carbongate.config.CarbonHome;
import io.carbongate.config.SettingsStore;
import io.carbongate.gateway.CarbonGateway;
import io.carbongate.integration.HostCatalog;
import io.carbongate.integration.InstallationDoctor;
import io.carbongate.integration.IntegrationGuideService;
import io.carbongate.integration.IntegrationInvocation;
import io.carbongate.integration.IntegrationManager;
import io.carbongate.integration.IntegrationRegistry;
import io.carbongate.integration.ProtectedRouteManager;
import io.carbongate.integration.SystemCommandRunner;
import io.carbongate.json.Json;
import io.carbongate.mcp.McpControlServer;
import io.carbongate.mcp.McpProfileService;
import io.carbongate.mcp.McpProfileStore;
import io.carbongate.mcp.McpProxy;
import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.model.Evaluation;
import io.carbongate.policy.EnforcementMode;
import io.carbongate.policy.PolicyEngine;
import io.carbongate.policy.PolicyProfile;
import io.carbongate.runtime.GuardService;
import io.carbongate.runtime.CarbonGateRuntime;
import io.carbongate.security.SecretScanner;
import io.carbongate.security.ShellRiskAnalyzer;

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.ArrayList;
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
            CarbonGateRuntime.fromConfig(CarbonHome.resolve(), PolicyProfile.BALANCED).audit()
                    .recordError("cli", e.getClass().getSimpleName() + ": " + e.getMessage());
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
            case "status" -> status(slice(args, 1));
            case "rules" -> rules();
            case "config" -> config(slice(args, 1));
            case "blocked" -> blocked(slice(args, 1));
            case "approvals" -> approvals(slice(args, 1));
            case "mode" -> mode(slice(args, 1));
            case "control" -> control(slice(args, 1));
            case "setup" -> setup(slice(args, 1));
            case "integrations" -> integrations(slice(args, 1));
            case "doctor" -> doctor(slice(args, 1));
            case "protect" -> protect(slice(args, 1));
            case "unprotect" -> unprotect(slice(args, 1));
            case "protections" -> protections(slice(args, 1));
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

    private static int status(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        RuntimeContext runtime = runtime(options.profile());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", runtime.settings().mode().name().toLowerCase());
        result.put("modeDescription", runtime.settings().mode().description());
        result.put("profile", options.profile().name().toLowerCase());
        result.put("carbonHome", runtime.settings().home().toString());
        result.put("settings", runtime.settings().path().toString());
        result.put("auditMode", runtime.settings().auditMode().name().toLowerCase());
        result.put("alertLocation", "终端 stderr；HTTP/MCP 调用响应");
        result.put("manualApproval", "carbon approvals list / carbon approvals approve <id>");
        result.put("pendingApprovals", runtime.approvals().pendingCount());
        if (runtime.audit() instanceof SecurityEventLog events) {
            SecurityEventLog.DailyStats stats = events.todayStats();
            result.put("blockedLog", events.blockedPath().toString());
            result.put("errorLog", events.errorPath().toString());
            result.put("logLevel", "ERROR only");
            result.put("todayBlockedEvents", stats.blockedEvents());
            result.put("todayInternalErrors", stats.internalErrors());
            result.put("todayLogBytes", stats.bytesWritten());
            result.put("dailyLogByteLimit", stats.byteLimit());
        } else if (runtime.audit() instanceof EnterpriseAuditLog enterprise) {
            EnterpriseAuditLog.Stats stats = enterprise.todayStats();
            result.put("auditLog", enterprise.todayPath().toString());
            result.put("logLevel", "INFO/WARN/ERROR detailed decisions");
            result.put("todayAllowed", stats.allowed());
            result.put("todayPendingApproval", stats.pendingApproval());
            result.put("todayDenied", stats.denied());
            result.put("todayInternalErrors", stats.internalErrors());
            result.put("todayLogBytes", stats.bytesWritten());
            result.put("dailyLogByteLimit", stats.byteLimit());
        }
        System.out.println(Json.stringify(result));
        return 0;
    }

    private static int rules() {
        SettingsStore settings = new SettingsStore(CarbonHome.resolve());
        var configuredRules = settings.rules();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeMode", settings.mode().name().toLowerCase());
        result.put("auditMode", settings.auditMode().name().toLowerCase());
        result.put("enabled", Map.of("shell", configuredRules.shell(),
                "filesystem", configuredRules.filesystem(), "network", configuredRules.network(),
                "secrets", configuredRules.secrets()));
        result.put("modes", List.of(
                Map.of("name", "balanced", "behavior", EnforcementMode.BALANCED.description()),
                Map.of("name", "warn", "behavior", EnforcementMode.WARN.description()),
                Map.of("name", "approval", "behavior", EnforcementMode.APPROVAL.description()),
                Map.of("name", "block", "behavior", EnforcementMode.BLOCK.description())
        ));
        result.put("shellRules", ShellRiskAnalyzer.catalog());
        result.put("filesystemRules", List.of("工作区内读取为低风险", "写入为中风险", "删除为高风险",
                "路径穿越或符号链接逃逸为严重风险"));
        result.put("networkRules", List.of("外部 GET 为中风险", "外部写入为高风险",
                "本地、私网、云 metadata 或含敏感信息外发为严重风险"));
        result.put("logging", settings.auditMode() == io.carbongate.config.AuditMode.LOCAL_MINIMAL
                ? "本地模式：只记录 ERROR 级别的完全拦截和内部错误；允许、警告、待授权不写日志"
                : "企业模式：详细记录允许、警告、待授权、批准、拦截和内部错误");
        System.out.println(Json.stringify(result));
        return 0;
    }

    private static int config(String[] args) throws Exception {
        SettingsStore settings = new SettingsStore(CarbonHome.resolve());
        String command = args.length == 0 ? "show" : args[0];
        return switch (command) {
            case "init" -> {
                boolean created = settings.initialize();
                System.out.println(Json.stringify(Map.of("path", settings.path().toString(),
                        "status", created ? "created" : "already_exists")));
                yield 0;
            }
            case "show" -> {
                System.out.println(Json.stringify(Map.of("path", settings.path().toString(),
                        "values", settings.snapshot())));
                yield 0;
            }
            case "path" -> {
                System.out.println(settings.path());
                yield 0;
            }
            case "set" -> {
                if (args.length != 3) {
                    throw new IllegalArgumentException("Usage: carbon config set <key> <value>");
                }
                RuntimeContext previousRuntime = runtime(PolicyProfile.BALANCED);
                if (args[1].equals(SettingsStore.AUDIT_MODE)) {
                    previousRuntime.audit().recordControl("audit_mode_change_requested",
                            Map.of("previous", settings.auditMode().name(), "requested", args[2]));
                }
                settings.set(args[1], args[2]);
                RuntimeContext changedRuntime = runtime(PolicyProfile.BALANCED);
                changedRuntime.audit().recordControl("configuration_changed",
                        Map.of("key", args[1], "value", settings.snapshot().get(args[1])));
                System.out.println(Json.stringify(Map.of("key", args[1], "value",
                        settings.snapshot().get(args[1]), "path", settings.path().toString(),
                        "effective", args[1].startsWith("audit.") ? "重启 Gateway 后生效" : "下一次 Tool Call 生效")));
                yield 0;
            }
            default -> throw new IllegalArgumentException("Usage: carbon config init|show|path|set <key> <value>");
        };
    }

    private static int blocked(String[] args) {
        int limit = 20;
        for (int index = 0; index < args.length; index++) {
            if (args[index].equals("--limit") && index + 1 < args.length) {
                limit = Integer.parseInt(args[++index]);
            } else {
                throw new IllegalArgumentException("Usage: carbon blocked [--limit 1..100]");
            }
        }
        RuntimeContext runtime = runtime(PolicyProfile.BALANCED);
        List<Map<String, Object>> blocked = runtime.audit() instanceof SecurityEventLog events
                ? events.recentBlocked(limit)
                : runtime.audit() instanceof EnterpriseAuditLog enterprise
                ? enterprise.recentBlocked(limit) : List.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("auditMode", runtime.settings().auditMode().name().toLowerCase());
        result.put("path", runtime.audit() instanceof SecurityEventLog events
                ? events.blockedPath().toString()
                : runtime.audit() instanceof EnterpriseAuditLog enterprise
                ? enterprise.todayPath().toString() : "");
        result.put("events", blocked);
        System.out.println(Json.stringify(result));
        return 0;
    }

    private static int approvals(String[] args) throws Exception {
        RuntimeContext runtime = runtime(PolicyProfile.BALANCED);
        AuthorizationStore approvals = runtime.approvals();
        String command = args.length == 0 ? "list" : args[0];
        if (command.equals("list")) {
            System.out.println(Json.stringify(Map.of("pending", approvals.pending())));
            return 0;
        }
        if (args.length != 2 || (!command.equals("approve") && !command.equals("deny"))) {
            throw new IllegalArgumentException("Usage: carbon approvals list|approve <id>|deny <id>");
        }
        boolean changed = command.equals("approve") ? runtime.guard().approve(args[1])
                : runtime.guard().denyApproval(args[1]);
        System.out.println(Json.stringify(Map.of("id", args[1], "status",
                changed ? (command.equals("approve") ? "approved_once" : "denied") : "not_found")));
        return changed ? 0 : 5;
    }

    private static int mode(String[] args) throws Exception {
        RuntimeContext runtime = runtime(PolicyProfile.BALANCED);
        SettingsStore settings = runtime.settings();
        if (args.length == 0 || (args.length == 1 && args[0].equals("show"))) {
            System.out.println(Json.stringify(Map.of("mode", settings.mode().name().toLowerCase(),
                    "description", settings.mode().description())));
            return 0;
        }
        int start = args[0].equals("set") ? 1 : 0;
        if (start >= args.length) throw new IllegalArgumentException("Usage: carbon mode set <自然语言级别>");
        return setMode(runtime, String.join(" ", Arrays.copyOfRange(args, start, args.length)));
    }

    private static int control(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Usage: carbon control <自然语言指令>");
        return setMode(runtime(PolicyProfile.BALANCED), String.join(" ", args));
    }

    private static int setup(String[] args) throws Exception {
        List<String> hosts = new ArrayList<>();
        boolean dryRun = false;
        for (int index = 0; index < args.length; index++) {
            switch (args[index]) {
                case "--host" -> {
                    if (++index >= args.length) throw new IllegalArgumentException("--host requires a value");
                    hosts.add(args[index]);
                }
                case "--all" -> hosts.addAll(HostCatalog.all().stream().map(host -> host.id()).toList());
                case "--dry-run" -> dryRun = true;
                default -> throw new IllegalArgumentException("Usage: carbon setup [--host HOST[,HOST...]] [--all] [--dry-run]");
            }
        }
        IntegrationManager manager = integrationManager();
        List<Map<String, Object>> results = manager.setup(hosts, !dryRun);
        System.out.println(Json.stringify(Map.of("dryRun", dryRun,
                "registry", manager.registry().path().toString(), "integrations", results)));
        boolean failed = results.stream().anyMatch(result -> {
            String state = String.valueOf(result.get("state"));
            return state.equals("unavailable") || state.contains("failed") || state.contains("timed_out")
                    || state.startsWith("conflict");
        });
        return failed ? 6 : 0;
    }

    private static int integrations(String[] args) throws Exception {
        IntegrationManager manager = integrationManager();
        String command = args.length == 0 ? "list" : args[0];
        return switch (command) {
            case "list", "status" -> {
                if (args.length > 1) throw new IllegalArgumentException("Usage: carbon integrations list");
                System.out.println(Json.stringify(Map.of("registry", manager.registry().path().toString(),
                        "integrations", manager.list(), "targets", integrationGuides().catalog())));
                yield 0;
            }
            case "remove" -> {
                if (args.length != 2) throw new IllegalArgumentException("Usage: carbon integrations remove <host>");
                Map<String, Object> result = manager.remove(args[1]);
                System.out.println(Json.stringify(result));
                yield Boolean.TRUE.equals(result.get("changed")) || "not_managed".equals(result.get("state")) ? 0 : 6;
            }
            case "guide" -> {
                if (args.length != 2) throw new IllegalArgumentException("Usage: carbon integrations guide <host>");
                System.out.println(Json.stringify(integrationGuides().guide(args[1])));
                yield 0;
            }
            case "export" -> {
                if (args.length != 2 && args.length != 4) {
                    throw new IllegalArgumentException("Usage: carbon integrations export <host> [--format descriptor|mcp-json|codex-toml]");
                }
                String format = "descriptor";
                if (args.length == 4) {
                    if (!args[2].equals("--format")) throw new IllegalArgumentException("Expected --format");
                    format = args[3];
                }
                Map<String, Object> result = integrationGuides().export(args[1], format);
                System.out.println(Json.stringify(result));
                yield Boolean.FALSE.equals(result.get("supported")) ? 6 : 0;
            }
            default -> throw new IllegalArgumentException("Usage: carbon integrations list|remove <host>|guide <host>|export <host> [--format FORMAT]");
        };
    }

    private static int doctor(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("Usage: carbon doctor");
        IntegrationManager manager = integrationManager();
        Map<String, Object> result = new InstallationDoctor(CarbonHome.resolve(), manager,
                IntegrationInvocation.current()).diagnose();
        System.out.println(Json.stringify(result));
        return Boolean.TRUE.equals(result.get("healthy")) ? 0 : 6;
    }

    private static int protect(String[] args) throws Exception {
        Path workspace = Path.of(".").toAbsolutePath().normalize();
        String name = null;
        String host = "codex";
        boolean dryRun = false;
        int delimiter = -1;
        boolean workspaceSet = false;
        for (int index = 0; index < args.length; index++) {
            switch (args[index]) {
                case "--name" -> name = requiredValue(args, ++index, "--name");
                case "--host" -> host = requiredValue(args, ++index, "--host");
                case "--dry-run" -> dryRun = true;
                case "--" -> {
                    delimiter = index;
                    index = args.length;
                }
                default -> {
                    if (args[index].startsWith("--") || workspaceSet) {
                        throw new IllegalArgumentException(protectUsage());
                    }
                    workspace = Path.of(args[index]).toAbsolutePath().normalize();
                    workspaceSet = true;
                }
            }
        }
        if (name == null || delimiter < 0 || delimiter + 1 >= args.length) {
            throw new IllegalArgumentException(protectUsage());
        }
        if (!Files.isDirectory(workspace)) throw new IllegalArgumentException("Protection workspace must be an existing directory");
        Map<String, Object> result = protectedRoutes().protect(host, name, workspace,
                List.of(Arrays.copyOfRange(args, delimiter + 1, args.length)), dryRun);
        runtime(PolicyProfile.BALANCED).audit().recordControl("protected_route_requested",
                Map.of("host", host, "name", name, "state", String.valueOf(result.get("state"))));
        System.out.println(Json.stringify(result));
        String state = String.valueOf(result.get("state"));
        return state.equals("protected") || state.equals("planned") || state.equals("already_protected") ? 0 : 6;
    }

    private static int unprotect(String[] args) throws Exception {
        if (args.length != 1 && args.length != 3) throw new IllegalArgumentException("Usage: carbon unprotect <name> [--host codex|generic]");
        String host = "codex";
        if (args.length == 3) {
            if (!args[1].equals("--host")) throw new IllegalArgumentException("Expected --host");
            host = args[2];
        }
        Map<String, Object> result = protectedRoutes().unprotect(host, args[0]);
        runtime(PolicyProfile.BALANCED).audit().recordControl("protected_route_removed",
                Map.of("host", host, "name", args[0], "state", String.valueOf(result.get("state"))));
        System.out.println(Json.stringify(result));
        String state = String.valueOf(result.get("state"));
        return state.equals("removed") || state.equals("not_managed") ? 0 : 6;
    }

    private static int protections(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !args[0].equals("list"))) {
            throw new IllegalArgumentException("Usage: carbon protections [list]");
        }
        ProtectedRouteManager manager = protectedRoutes();
        System.out.println(Json.stringify(Map.of("registry", manager.registry().path().toString(),
                "protections", manager.list())));
        return 0;
    }

    private static String requiredValue(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException(option + " requires a value");
        return args[index];
    }

    private static String protectUsage() {
        return "Usage: carbon protect [WORKSPACE] --name NAME [--host codex|generic] [--dry-run] -- SERVER [ARGS...]";
    }

    private static int setMode(RuntimeContext runtime, String instruction) throws Exception {
        SettingsStore settings = runtime.settings();
        EnforcementMode previous = settings.mode();
        EnforcementMode selected = EnforcementMode.fromNaturalLanguage(instruction);
        settings.setMode(selected);
        runtime.audit().recordControl("mode_changed", Map.of("previous", previous.name(),
                "mode", selected.name(), "instruction", instruction));
        System.out.println(Json.stringify(Map.of(
                "previous", previous.name().toLowerCase(),
                "mode", selected.name().toLowerCase(),
                "description", selected.description(),
                "effective", "下一次 Tool Call 立即生效"
        )));
        return 0;
    }

    private static int check(String[] args) {
        CliOptions options = CliOptions.parse(args);
        String command = requireCommand(options.trailing());
        SettingsStore settings = new SettingsStore(CarbonHome.resolve());
        Evaluation evaluation = new PolicyEngine(options.profile(), settings.mode(), settings.rules())
                .evaluate(Action.shell(command, options.workspace()));
        System.out.println(Json.stringify(evaluationMap(evaluation)));
        return evaluation.decision() == Decision.DENY ? 3 : 0;
    }

    private static int exec(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        Files.createDirectories(options.workspace());
        String command = requireCommand(options.trailing());
        GuardService guard = runtime(options.profile()).guard();
        Evaluation evaluation = guard.evaluate(Action.shell(command, options.workspace()));
        printEvaluation(evaluation);
        if (evaluation.decision() == Decision.DENY) return 3;
        if (evaluation.decision() == Decision.ASK) {
            if (!authorize(command, evaluation.id())) {
                System.err.println("待授权。运行 `carbon approvals approve " + evaluation.id() + "` 后重试。");
                return 4;
            }
            guard.discardApproval(evaluation.id());
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
        GuardService guard = runtime(options.profile()).guard();
        CarbonGateway gateway = new CarbonGateway(options.port(), options.workspace(), guard);
        Runtime.getRuntime().addShutdownHook(new Thread(gateway::close));
        gateway.start();
        System.err.printf("CarbonGate listening on http://127.0.0.1:%d (mode=%s, profile=%s, workspace=%s)%n",
                gateway.port(), guard.mode().name().toLowerCase(),
                options.profile().name().toLowerCase(), options.workspace());
        new CountDownLatch(1).await();
        return 0;
    }

    private static int mcp(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("serve")) {
            return new McpControlServer(CarbonHome.resolve(), PolicyProfile.BALANCED).run();
        }
        if (args.length > 0 && args[0].equals("profile")) {
            return mcpProfile(slice(args, 1));
        }
        if (args.length == 0 || !args[0].equals("proxy")) {
            throw new IllegalArgumentException("Usage: carbon mcp serve | carbon mcp proxy [options] -- SERVER [ARGS...] | carbon mcp profile COMMAND");
        }
        CliOptions options = CliOptions.parse(slice(args, 1));
        if (options.trailing().isEmpty()) throw new IllegalArgumentException("MCP server command is required");
        Files.createDirectories(options.workspace());
        return new McpProxy(options.workspace(), runtime(options.profile()).guard()).run(options.trailing());
    }

    private static int mcpProfile(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException(mcpProfileUsage());
        McpProfileStore store = new McpProfileStore(CarbonHome.resolve());
        McpProfileService profiles = new McpProfileService(store);
        return switch (args[0]) {
            case "list" -> {
                if (args.length != 1) throw new IllegalArgumentException(mcpProfileUsage());
                System.out.println(Json.stringify(Map.of("registry", store.path().toString(),
                        "coverage", "mcp_only", "profiles", store.list().stream().map(McpProfileStore.Profile::map).toList())));
                yield 0;
            }
            case "show" -> {
                if (args.length != 2) throw new IllegalArgumentException(mcpProfileUsage());
                System.out.println(Json.stringify(Map.of("registry", store.path().toString(),
                        "coverage", "mcp_only", "profile", store.require(args[1]).map())));
                yield 0;
            }
            case "add" -> addMcpProfile(store, args);
            case "remove" -> {
                if (args.length != 2) throw new IllegalArgumentException(mcpProfileUsage());
                boolean removed = store.remove(args[1]);
                runtime(PolicyProfile.BALANCED).audit().recordControl("mcp_profile_removed",
                        Map.of("name", args[1], "changed", removed));
                System.out.println(Json.stringify(Map.of("name", args[1],
                        "status", removed ? "removed" : "not_found", "registry", store.path().toString())));
                yield removed ? 0 : 5;
            }
            case "run" -> {
                if (args.length != 2) throw new IllegalArgumentException(mcpProfileUsage());
                McpProfileStore.Profile profile = store.require(args[1]);
                yield new McpProxy(profile.workspace(), runtime(PolicyProfile.BALANCED).guard()).run(profile.command());
            }
            case "export" -> {
                if (args.length != 2 && args.length != 4) throw new IllegalArgumentException(mcpProfileUsage());
                String format = "descriptor";
                if (args.length == 4) {
                    if (!args[2].equals("--format")) throw new IllegalArgumentException("Expected --format");
                    format = args[3];
                }
                System.out.println(Json.stringify(profiles.export(args[1], format)));
                yield 0;
            }
            default -> throw new IllegalArgumentException(mcpProfileUsage());
        };
    }

    private static int addMcpProfile(McpProfileStore store, String[] args) throws Exception {
        if (args.length < 4) throw new IllegalArgumentException(mcpProfileUsage());
        String name = args[1];
        Path workspace = Path.of(System.getProperty("user.dir"));
        boolean replace = false;
        int delimiter = -1;
        for (int index = 2; index < args.length; index++) {
            switch (args[index]) {
                case "--workspace" -> {
                    if (++index >= args.length) throw new IllegalArgumentException("--workspace requires a path");
                    workspace = Path.of(args[index]);
                }
                case "--replace" -> replace = true;
                case "--" -> {
                    delimiter = index;
                    index = args.length;
                }
                default -> throw new IllegalArgumentException("Unknown MCP profile add option: " + args[index]);
            }
        }
        if (delimiter < 0 || delimiter + 1 >= args.length) {
            throw new IllegalArgumentException("MCP server command is required after --");
        }
        McpProfileStore.Profile profile = store.put(name, workspace,
                List.of(Arrays.copyOfRange(args, delimiter + 1, args.length)), replace);
        runtime(PolicyProfile.BALANCED).audit().recordControl("mcp_profile_saved",
                Map.of("name", profile.name(), "workspace", profile.workspace().toString(), "replaced", replace));
        System.out.println(Json.stringify(Map.of("status", replace ? "saved_or_replaced" : "created",
                "registry", store.path().toString(), "coverage", "mcp_only", "profile", profile.map())));
        return 0;
    }

    private static String mcpProfileUsage() {
        return "Usage: carbon mcp profile list|show <name>|add <name> [--workspace PATH] [--replace] -- SERVER [ARGS...]|remove <name>|run <name>|export <name> [--format descriptor|mcp-json|codex-toml]";
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
        GuardService guard = runtime(options.profile()).guard();
        try (CarbonGateway gateway = new CarbonGateway(options.port(), options.workspace(), guard)) {
            gateway.start();
            ProcessBuilder builder = new ProcessBuilder(options.trailing())
                    .directory(options.workspace().toFile())
                    .inheritIO();
            builder.environment().put("CARBON_ENDPOINT", "http://127.0.0.1:" + gateway.port());
            builder.environment().put("CARBON_WORKSPACE", options.workspace().toString());
            builder.environment().put("CARBON_PROFILE", options.profile().name().toLowerCase());
            builder.environment().put("CARBON_MODE", guard.mode().name().toLowerCase());
            return builder.start().waitFor();
        }
    }

    private static int version() {
        System.out.println("CarbonGate 0.2.0 (Java 21)");
        return 0;
    }

    private static RuntimeContext runtime(PolicyProfile profile) {
        CarbonGateRuntime.Instance instance = CarbonGateRuntime.fromConfig(CarbonHome.resolve(), profile);
        return new RuntimeContext(instance.settings(), instance.approvals(), instance.audit(), instance.guard());
    }

    private static IntegrationManager integrationManager() {
        return new IntegrationManager(new IntegrationRegistry(CarbonHome.resolve()),
                new SystemCommandRunner(), HostCatalog.all(), IntegrationInvocation.current());
    }

    private static IntegrationGuideService integrationGuides() {
        return new IntegrationGuideService(IntegrationInvocation.current());
    }

    private static ProtectedRouteManager protectedRoutes() {
        return new ProtectedRouteManager(CarbonHome.resolve(), new SystemCommandRunner());
    }

    private static boolean authorize(String command, String id) {
        if ("allow".equalsIgnoreCase(System.getenv("CARBON_NON_INTERACTIVE"))) return true;
        Console console = System.console();
        if (console == null) return false;
        String answer = console.readLine("CarbonGate 手动授权 [%s]，仅允许一次？[y/N]%n%s%n> ", id, command);
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

    private static void printEvaluation(Evaluation evaluation) {
        String label = evaluation.decision() == Decision.ALLOW &&
                evaluation.risk().ordinal() >= io.carbongate.model.RiskLevel.MEDIUM.ordinal()
                ? "WARNING" : evaluation.decision().name();
        System.err.printf("CarbonGate %s [%s]: %s%n", label,
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
        return Arrays.copyOfRange(values, from, values.length);
    }

    private static void usage() {
        System.out.println("""
                CarbonGate — zero-trust runtime gateway for AI agents

                Query and control:
                  carbon status
                  carbon rules
                  carbon config init|show|path|set <key> <value>
                  carbon blocked [--limit 20]
                  carbon approvals list|approve <id>|deny <id>
                  carbon mode show|set <自然语言级别>
                  carbon control "切换到警告/每次授权/全部禁止/平衡模式"

                Host integration:
                  carbon protect [WORKSPACE] --name NAME [--host codex|generic] -- SERVER [ARGS...]
                  carbon protections [list]
                  carbon unprotect <name> [--host codex|generic]
                  carbon setup [--host codex,claude,...] [--all] [--dry-run]
                  carbon integrations list|remove <host>|guide <host>|export <host> [--format FORMAT]
                  carbon doctor

                Execution:
                  carbon check [--profile PROFILE] [--workspace PATH] -- COMMAND
                  carbon exec [--profile PROFILE] [--workspace PATH] -- COMMAND
                  carbon gateway [--profile PROFILE] [--workspace PATH] [--port 8765]
                  carbon mcp proxy [--profile PROFILE] [--workspace PATH] -- SERVER [ARGS...]
                  carbon mcp serve
                  carbon mcp profile list|show <name>|add <name> [--workspace PATH] -- SERVER [ARGS...]
                  carbon mcp profile remove|run <name>|export <name> [--format FORMAT]
                  carbon redact TEXT
                  carbon run [--workspace PATH] -- AGENT [ARGS...]
                  carbon version
                """);
    }

    private record RuntimeContext(SettingsStore settings, AuthorizationStore approvals,
                                  AuditSink audit, GuardService guard) {}
}
