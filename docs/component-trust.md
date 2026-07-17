# Component signing and trust policy

CarbonGate can authenticate `.carbon` publishers with JDK 21's built-in
Ed25519 implementation. No third-party cryptography library is used.

Generate an offline signing key and trust its public half:

```bash
./build/carbon-enterprise trust generate company-release /secure/key-directory
./build/carbon-enterprise trust add company-release /secure/key-directory/company-release.public.x509
```

Sign while packaging:

```bash
./build/carbon-enterprise package component-source component.carbon \
  --sign company-release /secure/key-directory/company-release.private.pk8
```

After all required publisher keys are installed, enforce signed packages:

```bash
./build/carbon-enterprise trust policy require_signed
./build/carbon-enterprise trust status
```

The default is `allow_unsigned` for backward compatibility. A package carrying
a signature is always verified and rejected when its key is unknown or its
signed material changed. Under `require_signed`, unsigned packages are rejected.

The signature covers the hashes of `manifest.json`, `checksums.json`, `LICENSE`,
and `NOTICE`; the signed checksum document covers every payload file. Private
keys are raw PKCS#8 files used only by the packaging command and are never
copied into an archive. Generated private keys receive owner-only POSIX
permissions where supported. Keep them outside the repository and component
store and protect them using the organization's normal key-management process.
