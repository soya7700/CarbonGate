#!/usr/bin/env node
import { main } from "../lib/adapter.js";

main(process.argv.slice(2)).catch((error) => {
  process.stderr.write(`CarbonGate npm adapter: ${error.message}\n`);
  process.exitCode = 1;
});
