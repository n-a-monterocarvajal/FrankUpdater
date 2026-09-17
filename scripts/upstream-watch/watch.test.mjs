import test from 'node:test';
import assert from 'node:assert/strict';
import { impact, inspect, repository } from './watch.mjs';

test('relevance retains uncertainty and detects renamed paths and changed symbols', () => {
  assert.equal(impact({ symbols: ['AbiMatcher'] }, [{ path: 'a.kt', patch: '-class AbiMatcher' }]).status, 'review-required');
  assert.equal(impact({ symbols: ['AbiMatcher'] }, [{ path: 'a.kt', patch: 'other' }]).status, 'unknown-impact');
  assert.equal(impact({ paths: ['src/matcher.kt'] }, [{ path: 'renamed.kt', previousPath: 'src/matcher.kt', patch: '' }]).status, 'review-required');
  assert.equal(impact({ paths: ['src'] }, [{ path: 'README.md', patch: 'docs' }]).status, 'outside-watched-paths');
  assert.equal(impact({ paths: ['src'] }, [], true).status, 'unknown-impact');
});

test('observation never advances reviewed or integrated, and comparisons start at reviewed', async () => {
  const old = 'a'.repeat(40), seen = 'b'.repeat(40), head = 'c'.repeat(40);
  const calls = [];
  const result = await inspect({ upstream: 'https://github.com/example/project', last_seen_commit: seen,
    last_reviewed_commit: old, last_integrated_commit: null, symbols: ['Matcher'] }, async url => {
    calls.push(url);
    return url.includes('/compare/') ? { status: 'ahead', files: [{ filename: 'Matcher.kt', patch: '+fix' }] } : [{ sha: head }];
  });
  assert.ok(calls[1].endsWith(`${old}...${head}`));
  assert.equal(result.last_seen_commit, head);
  assert.equal(result.last_reviewed_commit, old);
  assert.equal(result.last_integrated_commit, null);
  assert.equal(result.status, 'review-required');
  assert.throws(() => repository('https://github.com.evil.invalid/a/b'));
  assert.throws(() => repository('https://user:token@github.com/a/b'));
});

test('GitLab truncated comparisons remain unresolved', async () => {
  const result = await inspect({ upstream: 'https://gitlab.com/group/project', ref: 'a'.repeat(40) }, async url =>
    url.includes('/compare?') ? { compare_timeout: true, diffs: [] } : [{ id: 'b'.repeat(40) }]);
  assert.equal(result.status, 'unknown-impact');
});
