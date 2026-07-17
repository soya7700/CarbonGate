# Approval Policy Provider v1

The optional Approval Policy Provider implements the `authorize` stage of the
Enterprise Guard Pipeline. It is stateless, has no credentials, and returns one
of `allow`, `ask`, or `deny`.

```bash
./scripts/build-approval.sh
./build/carbon-enterprise install build/approval-policy-provider-1.0.0.carbon
./build/carbon-enterprise enable approval-policy-provider 1.0.0
./build/carbon-enterprise guard '{"action":"shell","risk":"high"}'
```

Windows uses `scripts\build-approval.ps1` and
`build\carbon-enterprise.cmd`.

The default v1 policy denies critical risk, asks for high risk, asks for
medium-risk shell/filesystem/egress/network/sandbox actions, and allows the
remaining low or medium risk. A prior inspection denial always remains denied.

An `ask` result is only an approval requirement. It is never represented as a
human approval receipt, and it does not allow Sandbox execution. The caller
must complete approval through its own trusted UI or identity workflow and
submit a new authorized operation through an integration designed for that
identity system.
