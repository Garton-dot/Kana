#!/usr/bin/env node
/*
 * Inline the stylesheet and scripts into one self-contained HTML file.
 *   node scripts/bundle.js [out]              -> full document (default dist/kana.html)
 *   node scripts/bundle.js out --fragment     -> <title>/<style> + body content only,
 *                                                for hosts that supply their own skeleton
 */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const args = process.argv.slice(2);
const fragment = args.includes('--fragment');
const out = args.find((a) => !a.startsWith('--')) || path.join(root, 'dist', 'kana.html');

const read = (rel) => fs.readFileSync(path.join(root, rel), 'utf8');
// Keep "</script>" inside inlined code from closing the tag early.
const safeScript = (code) => code.replace(/<\/script/gi, '<\\/script');

let html = read('index.html')
  .replace(/<link rel="stylesheet" href="([^"]+)">/g, (_, href) => `<style>\n${read(href)}</style>`)
  .replace(/<script src="([^"]+)"><\/script>/g, (_, src) => `<script>\n${safeScript(read(src))}</script>`);

if (fragment) {
  const title = html.match(/<title>[\s\S]*?<\/title>/)[0];
  const styles = html.match(/<style>[\s\S]*?<\/style>/g).join('\n');
  const body = html.match(/<body>([\s\S]*)<\/body>/)[1];
  html = `${title}\n${styles}\n${body}`;
}

fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, html);
console.log(`Wrote ${out} (${(html.length / 1024).toFixed(1)} KB)`);
