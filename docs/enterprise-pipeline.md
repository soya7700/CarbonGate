# Enterprise Guard Pipeline v1

The optional Enterprise Guard Pipeline makes installed components useful as a
single explicit decision path:

```text
inspect -> authorize -> sandbox (only after allow) -> audit
```

It is invoked with `carbon-enterprise guard JSON`; it does not silently attach
to CarbonGate Core or an Agent's built-in tools.

```bash
./build/carbon-enterprise guard \
  '{"action":"shell","risk":"high","content":"request summary","sandbox":{"componentId":"container-sandbox","payload":{"workspace":"/project","image":"registry/tool@sha256:...","command":["tool","check"]}}}'
```

Inspect results map `block` to `deny` and `review` to `ask`. Authorizers return
`allow`, `ask`, or `deny`. A Sandbox runs only when the combined decision is
`allow`. Audit components receive a generated event ID, action, risk, final
decision, and component IDs—never the original inspected content or sandbox
output.

Every failed component obeys its manifest's `failureMode`. `fail_closed`
changes the pipeline decision to `deny`; `fail_open` continues and is visible
as a failed step. The result reports only compact component summaries, except
for an explicitly requested Sandbox result needed by the caller.
