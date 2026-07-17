package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates deterministic .carbon archives from a reviewed component directory. */
public final class ComponentPackageBuilder {
    private static final long MAX_FILE_BYTES = 20_000_000L;
    private static final int MAX_FILES = 512;

    public ComponentManifest build(Path sourceDirectory, Path outputArchive) throws IOException {
        return build(sourceDirectory, outputArchive, null, null);
    }

    public ComponentManifest build(Path sourceDirectory, Path outputArchive, String keyId, Path privateKey)
            throws IOException {
        Path source = sourceDirectory.toAbsolutePath().normalize();
        Path output = outputArchive.toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) throw new IllegalArgumentException("Component source must be a directory");
        if (!output.getFileName().toString().endsWith(".carbon")) {
            throw new IllegalArgumentException("Component output must end with .carbon");
        }
        requireRegular(source.resolve("manifest.json"));
        requireRegular(source.resolve("LICENSE"));
        requireRegular(source.resolve("NOTICE"));
        if (Files.exists(source.resolve("checksums.json"))) {
            throw new IllegalArgumentException("Source must not contain generated checksums.json");
        }
        ComponentManifest manifest = ComponentManifest.read(source.resolve("manifest.json"));
        if (manifest.kind() == ComponentManifest.Kind.PACK) {
            PackDocument.read(source.resolve("payload").resolve("pack.json"));
        }
        List<Path> payload = regularFiles(source.resolve("payload"));
        Map<String, Object> hashes = new LinkedHashMap<>();
        for (Path file : payload) hashes.put(relative(source, file), sha256(file));
        byte[] checksums = Json.stringify(Map.of("algorithm", "SHA-256", "files", hashes))
                .getBytes(StandardCharsets.UTF_8);
        byte[] signature = signature(source, output, checksums, keyId, privateKey);

        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        Files.deleteIfExists(temporary);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
            add(zip, source.resolve("manifest.json"), "manifest.json");
            addBytes(zip, checksums, "checksums.json");
            add(zip, source.resolve("LICENSE"), "LICENSE");
            add(zip, source.resolve("NOTICE"), "NOTICE");
            if (signature != null) addBytes(zip, signature, "signature.json");
            for (Path file : payload) add(zip, file, relative(source, file));
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(temporary);
            throw error;
        }
        move(temporary, output);
        return manifest;
    }

    private byte[] signature(Path source, Path output, byte[] checksums, String keyId, Path privateKey)
            throws IOException {
        if (keyId == null && privateKey == null) return null;
        if (keyId == null || privateKey == null || !keyId.matches("[a-z][a-z0-9.-]{2,63}")) {
            throw new IllegalArgumentException("Signing requires a valid key id and private key file");
        }
        Files.createDirectories(output.getParent());
        Path material = Files.createTempDirectory(output.getParent(), ".carbon-sign-");
        try {
            Files.copy(source.resolve("manifest.json"), material.resolve("manifest.json"));
            Files.write(material.resolve("checksums.json"), checksums);
            Files.copy(source.resolve("LICENSE"), material.resolve("LICENSE"));
            Files.copy(source.resolve("NOTICE"), material.resolve("NOTICE"));
            byte[] value = ComponentTrustStore.sign(privateKey, material);
            return Json.stringify(Map.of("apiVersion", ComponentTrustStore.SIGNATURE_API,
                    "algorithm", "Ed25519", "keyId", keyId,
                    "signature", Base64.getEncoder().encodeToString(value))).getBytes(StandardCharsets.UTF_8);
        } finally {
            try (var paths = Files.walk(material)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private List<Path> regularFiles(Path payload) throws IOException {
        if (!Files.isDirectory(payload)) return List.of();
        try (var paths = Files.walk(payload)) {
            List<Path> files = paths.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList();
            if (files.size() > MAX_FILES) throw new IllegalArgumentException("Component payload has too many files");
            for (Path file : files) {
                if (Files.isSymbolicLink(file) || Files.size(file) > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("Component payload file is unsafe or exceeds 20 MB");
                }
            }
            return files;
        }
    }

    private void add(ZipOutputStream zip, Path file, String name) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) throw new IllegalArgumentException("Component metadata exceeds 20 MB");
        try (InputStream input = Files.newInputStream(file)) {
            addEntry(zip, input, name);
        }
    }

    private void addBytes(ZipOutputStream zip, byte[] value, String name) throws IOException {
        ZipEntry entry = entry(name);
        zip.putNextEntry(entry);
        zip.write(value);
        zip.closeEntry();
    }

    private void addEntry(ZipOutputStream zip, InputStream input, String name) throws IOException {
        zip.putNextEntry(entry(name));
        input.transferTo(zip);
        zip.closeEntry();
    }

    private ZipEntry entry(String name) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        return entry;
    }

    private String relative(Path source, Path file) {
        return source.relativize(file).toString().replace(java.io.File.separatorChar, '/');
    }

    private String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void requireRegular(Path file) {
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException("Component source is missing safe " + file.getFileName());
        }
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
