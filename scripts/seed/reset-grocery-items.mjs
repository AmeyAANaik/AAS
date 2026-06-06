#!/usr/bin/env node

process.env.RESET_GROCERY = '1';
if (!process.env.PRESERVE_OLD_ITEMS) {
  process.env.PRESERVE_OLD_ITEMS = '0';
}
await import('./items.mjs');
