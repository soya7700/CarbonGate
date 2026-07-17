package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Local Ed25519 publisher trust policy for .carbon packages. */
public final class ComponentTrustStore {
    public static final String SIGNATURE_API = "carbongate.signature/v1";
    private static final Set<String> SIGNED_FILES = Set.of("manifest.json", "checksums.json", "LICENSE", "NOTICE");
    private final Path file;

    public ComponentTrustStore(Path carbonHome) {
        file = carbonHome.toAbsolutePath().normalize().resolve("enterprise").resolve("trust.json");
    }

    public synchronized Map<String, Object> status() throws IOException {
        State state = read();
        return Map.of("policy", state.policy(), "keys", state.keys().keySet().stream().sorted().toList(),
                "file", file.toString());
    }

    public synchronized void add(String keyId, Path publicKeyFile) throws IOException {
        validateKeyId(keyId);
        byte[] encoded = Files.readAllBytes(publicKeyFile);
        publicKey(encoded);
        State state = read();
        Map<String, String> keys = new LinkedHashMap<>(state.keys());
        String value = Base64.getEncoder().encodeToString(encoded);
        String existing = keys.get(keyId);
        if (existing != null && !existing.equals(value)) {
            throw new IllegalArgumentException("Trust key id already belongs to a different public key");
        }
        keys.put(keyId, value);
        write(new State(state.policy(), Map.copyOf(keys)));
    }

    public synchronized void policy(String policy) throws IOException {
        if (!policy.equals("allow_unsigned") && !policy.equals("require_signed")) {
            throw new IllegalArgumentException("Trust policy must be allow_unsigned or require_signed");
        }
        State state = read();
        write(new State(policy, state.keys()));
    }

    public synchronized Map<String, Object> generate(String keyId, Path directory) throws IOException {
        validateKeyId(keyId);
        try {
            var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            Files.createDirectories(directory);
            Path privateFile = directory.resolve(keyId + ".private.pk8");
            Path publicFile = directory.resolve(keyId + ".public.x509");
            if (Files.exists(privateFile) || Files.exists(publicFile)) throw new IllegalArgumentException("Key file already exists");
            Files.write(privateFile, pair.getPrivate().getEncoded());
            Files.write(publicFile, pair.getPublic().getEncoded());
            try {
                Files.setPosixFilePermissions(privateFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows ACLs are managed by the user; the file is never copied into a package.
            }
            return Map.of("keyId", keyId, "privateKey", privateFile.toString(), "publicKey", publicFile.toString());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Ed25519 is unavailable", error);
        }
    }

    public void verify(Path extracted) throws IOException {
        State state = read();
        Path signatureFile = extracted.resolve("signature.json");
        if (!Files.isRegularFile(signatureFile)) {
            if (state.policy().equals("require_signed")) throw new IllegalArgumentException("Trust policy requires a signed component");
            return;
        }
        Map<String, Object> value = Json.object(Files.readString(signatureFile, StandardCharsets.UTF_8));
        if (!SIGNATURE_API.equals(value.get("apiVersion")) || !"Ed25519".equals(value.get("algorithm"))) {
            throw new IllegalArgumentException("Unsupported component signature format");
        }
        String keyId = String.valueOf(value.get("keyId"));
        String encodedKey = state.keys().get(keyId);
        if (encodedKey == null) throw new IllegalArgumentException("Component signer is not trusted: " + keyId);
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(String.valueOf(value.get("signature")));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey(Base64.getDecoder().decode(encodedKey)));
            verifier.update(message(extracted));
            if (!verifier.verify(signatureBytes)) throw new IllegalArgumentException("Component signature verification failed");
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            if (error instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("Component signature verification failed", error);
        }
    }

    public static byte[] sign(Path privateKeyFile, Path materialDirectory) throws IOException {
        try {
            PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(
                    new PKCS8EncodedKeySpec(Files.readAllBytes(privateKeyFile)));
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(key);
            signer.update(message(materialDirectory));
            return signer.sign();
        } catch (GeneralSecurityException error) {
            throw new IllegalArgumentException("Invalid Ed25519 private key", error);
        }
    }

    private static byte[] message(Path directory) throws IOException {
        StringBuilder message = new StringBuilder(SIGNATURE_API).append('\n');
        for (String name : SIGNED_FILES.stream().sorted().toList()) {
            Path path = directory.resolve(name);
            if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Signed component is missing " + name);
            message.append(name).append(':').append(sha256(Files.readAllBytes(path))).append('\n');
        }
        return message.toString().getBytes(StandardCharsets.UTF_8);
    }

    private State read() throws IOException {
        if (!Files.isRegularFile(file)) return new State("allow_unsigned", Map.of());
        Map<String, Object> value = Json.object(Files.readString(file, StandardCharsets.UTF_8));
        String policy = String.valueOf(value.get("policy"));
        if (!policy.equals("allow_unsigned") && !policy.equals("require_signed")) throw new IllegalArgumentException("Invalid trust policy file");
        Object raw = value.get("keys");
        if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException("Invalid trust key map");
        Map<String, String> keys = new LinkedHashMap<>();
        map.forEach((key, encoded) -> keys.put(String.valueOf(key), String.valueOf(encoded)));
        return new State(policy, Map.copyOf(keys));
    }

    private void write(State state) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling("trust.json.tmp");
        Files.writeString(temporary, Json.stringify(Map.of("version", 1, "policy", state.policy(), "keys", state.keys())),
                StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static PublicKey publicKey(byte[] encoded) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException error) {
            throw new IllegalArgumentException("Invalid Ed25519 public key", error);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void validateKeyId(String keyId) {
        if (keyId == null || !keyId.matches("[a-z][a-z0-9.-]{2,63}")) throw new IllegalArgumentException("Invalid trust key id");
    }

    private record State(String policy, Map<String, String> keys) {}
}
