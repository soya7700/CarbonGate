# Third-party notices

The CarbonGate Java runtime archive has no third-party source or runtime
dependencies. It builds and runs only against documented Java SE APIs provided
by the user's JDK. Native CLI archives contain the runtime portions linked by
GraalVM Community Native Image, which are covered by GPL-2.0-only with the
Classpath Exception. CarbonGate remains Apache-2.0 under that exception.

The following development-only components are invoked by GitHub Actions. They
are not bundled in CarbonGate artifacts:

| Component | Pinned version | Source | License |
|---|---|---|---|
| actions/checkout | `34e114876b0b11c390a56381ad16ebd13914f8d5` | https://github.com/actions/checkout | MIT |
| actions/setup-java | `c1e323688fd81a25caa38c78aa6df2d33d3e20d9` | https://github.com/actions/setup-java | MIT |
| graalvm/setup-graalvm | `0def53c0fd8534bc13416c9469f5be45265824fd` | https://github.com/graalvm/setup-graalvm | UPL-1.0 |
| Eclipse Temurin JDK | `21.0.11+10` | https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.11%2B10 | GPL-2.0-only with Classpath Exception |
| GraalVM Community Native Image | `25.1.3 / JDK 25.0.3` | https://github.com/graalvm/graalvm-ce-builds/releases/tag/graal-25.1.3 | GPL-2.0-only with Classpath Exception |

`actions/checkout` and `actions/setup-java` include this notice:

> The MIT License (MIT)
> Copyright (c) 2018 GitHub, Inc. and contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

The operating system, shell, JDK, and external programs launched by a user are
not distributed as part of CarbonGate and retain their respective licenses.
The optional Container Sandbox Provider can invoke a user-installed Docker or
Podman CLI. CarbonGate does not download, bundle, or redistribute either
program; users remain responsible for its license and security updates.

This file must be updated before a third-party component, copied source,
generated client, binary, container base image, or bundled asset is distributed.
