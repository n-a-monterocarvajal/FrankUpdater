// SPDX-License-Identifier: GPL-3.0-or-later
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parse } from 'yaml';

export function repository(value) {
  const url = new URL(value);
  if (url.protocol !== 'https:' || url.username || url.password || url.port ||
      !['github.com', 'gitlab.com'].includes(url.hostname)) throw new Error('Unsupported upstream URL');
  const parts = url.pathname.replace(/\.git$/, '').split('/').filter(Boolean);
  if (parts.length < 2 || (url.hostname === 'github.com' && parts.length !== 2) ||
      parts.some(part => !/^[\w.-]+$/.test(part))) throw new Error('Invalid repository path');
  return { host: url.hostname, path: parts.join('/') };
}

export function impact(component, files, incomplete = false) {
  const paths = component.paths ?? [];
  const symbols = component.symbols ?? [];
  const matches = files.filter(file => paths.some(path =>
    file.path === path || file.path.startsWith(path.replace(/\/$/, '') + '/') || file.previousPath === path) ||
    symbols.some(symbol => [symbol, ...symbol.split('.')].filter(part => part.length > 3)
      .some(part => `${file.path}\n${file.previousPath ?? ''}\n${file.patch ?? ''}`.includes(part))));
  if (matches.length) return { status: 'review-required', files: matches.map(file => file.path) };
  // Symbol absence in a patch cannot prove that behavior or dependencies did not change.
  if (incomplete || paths.length === 0 || files.some(file => file.patch == null))
    return { status: 'unknown-impact', files: files.map(file => file.path) };
  return { status: 'outside-watched-paths', files: [] };
}

export function apiReader(token = process.env.GITHUB_TOKEN) {
  const cache = new Map();
  return async url => {
    const target = new URL(url);
    if (!['api.github.com', 'gitlab.com'].includes(target.hostname) || target.protocol !== 'https:')
      throw new Error('Invalid API host');
    if (!cache.has(url)) cache.set(url, (async () => {
      const headers = { 'User-Agent': 'FrankUpdater-upstream-watch', Accept: 'application/json' };
      if (token && target.hostname === 'api.github.com') headers.Authorization = `Bearer ${token}`;
      const response = await fetch(url, { headers, redirect: 'error', signal: AbortSignal.timeout(30000) });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return response.json();
    })());
    return cache.get(url);
  };
}

export async function inspect(component, get) {
  const repo = repository(component.upstream);
  const base = component.last_reviewed_commit ?? component.last_integrated_commit ?? component.ref;
  let head, compare;
  if (repo.host === 'github.com') {
    const root = `https://api.github.com/repos/${repo.path}`;
    head = (await get(`${root}/commits?per_page=1`))[0]?.sha;
    compare = async () => {
      const data = await get(`${root}/compare/${base}...${head}`);
      return { files: (data.files ?? []).map(file => ({ path: file.filename, previousPath: file.previous_filename, patch: file.patch })),
        incomplete: (data.files?.length ?? 0) >= 300 || data.status === 'diverged' || !Array.isArray(data.files) };
    };
  } else {
    const root = `https://gitlab.com/api/v4/projects/${encodeURIComponent(repo.path)}/repository`;
    head = (await get(`${root}/commits?per_page=1`))[0]?.id;
    compare = async () => {
      const data = await get(`${root}/compare?from=${base}&to=${head}&straight=true`);
      return { files: (data.diffs ?? []).map(file => ({ path: file.new_path, previousPath: file.old_path, patch: file.diff })),
        incomplete: data.compare_timeout || !Array.isArray(data.diffs) || data.diffs.some(file => file.collapsed || file.too_large) };
    };
  }
  if (!/^[a-f0-9]{40,64}$/.test(head ?? '')) throw new Error('Invalid upstream commit');
  const state = { last_seen_commit: head, last_reviewed_commit: component.last_reviewed_commit ?? null,
    last_integrated_commit: component.last_integrated_commit ?? null };
  if (!base) return { ...state, status: 'baseline-required', files: [] };
  if (!/^[a-f0-9]{40,64}$/.test(base)) throw new Error('Baseline must be an immutable commit');
  if (base === head) return { ...state, status: 'reviewed-current', files: [] };
  const change = await compare();
  return { ...state, compared_from: base, ...impact(component, change.files, change.incomplete) };
}

export async function run(manifestPath, outputDirectory, offline = false) {
  const manifest = parse(await readFile(manifestPath, 'utf8'));
  if (manifest.schema_version !== 1 || !manifest.components) throw new Error('Unsupported manifest');
  const get = apiReader();
  const report = { generated_at: new Date().toISOString(), network: !offline, components: {} };
  for (const [name, component] of Object.entries(manifest.components)) {
    repository(component.upstream);
    try {
      report.components[name] = offline ? { ...component, status: 'not-queried', files: [] } : await inspect(component, get);
    } catch (error) {
      report.components[name] = { last_seen_commit: component.last_seen_commit ?? null,
        last_reviewed_commit: component.last_reviewed_commit ?? null,
        last_integrated_commit: component.last_integrated_commit ?? null,
        status: 'query-failed', error: /^HTTP \d+$/.test(error.message) ? error.message : error.name, files: [] };
    }
  }
  const escape = value => String(value ?? '—').replace(/[|<>\r\n]/g, ' ');
  const lines = ['# Upstream dashboard', '', `Generated: ${report.generated_at}. Network: ${report.network}.`, '',
    'Observation is not review or integration. This report never merges or edits upstream pins.', '',
    '| Component | Status | Last seen | Last reviewed | Last integrated |', '|---|---|---|---|---|'];
  for (const [name, state] of Object.entries(report.components)) {
    lines.push(`| ${escape(name)} | ${escape(state.status)} | ${escape(state.last_seen_commit)} | ${escape(state.last_reviewed_commit)} | ${escape(state.last_integrated_commit)} |`);
  }
  for (const [name, state] of Object.entries(report.components)) {
    if (state.files.length) lines.push('', `## ${escape(name)}`, '', ...state.files.map(path => `- ${escape(path)}`));
  }
  await mkdir(outputDirectory, { recursive: true });
  await writeFile(resolve(outputDirectory, 'report.json'), JSON.stringify(report, null, 2) + '\n');
  await writeFile(resolve(outputDirectory, 'dashboard.md'), lines.join('\n') + '\n');
  return report;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const args = process.argv.slice(2).filter(arg => arg !== '--offline');
  await run(args[0] ?? 'UPSTREAMS.yml', args[1] ?? 'build/upstream-watch', process.argv.includes('--offline'));
}
