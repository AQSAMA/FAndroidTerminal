# Phone-feedback follow-up

The supplied phone screenshots showed fragmented settings, a variable-height
quick-key dock and a failed external file-browser handoff. PR 10 is retained in
the base. Three independent GPT-6.1 Sol reviews with high reasoning effort were
requested and completed; no fourth review was started.

## Round 1: workflow and design

- Restore distinct always-visible and arrow-expanded key rows. The two saved lists
  now retain row membership, fixed cell widths and a separate available-key palette.
  Legacy layouts migrate without discarding keys, including a literal pipe key.
- Remove deliberate blank frames: route opacity no longer resets to zero; nested
  settings Crossfade and content-size animation are removed. Requested settings
  destinations are displayed immediately while retained state is acknowledged.
- Combine thin settings in Terminal & tools: startup selector, home/start paths,
  individually edited saved commands, Linux tool entry points and folded key help.
- Implement the missing DocumentsProvider findDocumentPath contract with confined
  document ancestry; reduce Files chrome and distinguish loading/empty/error states.
- Preserve raw inherited shell/directory defaults when saving package profiles.

## Round 2: state, restoration and capability preservation

- Restored key layouts can invalidate selection: guard membership and movement
  indices; do not reinsert an absent selected key.
- Restoring command lists during dialog editing can redirect an index: retain the
  draft, block confirmation and offer explicit recovery as a new command.
- Stored startDirectory can differ from inherited launch defaults: share the real
  resolution rule, validate on IO under file admission, guard stale callbacks and
  expose Refresh. A running guest remains subject to existing file locks.
- Preserve storage measurement in the Files overflow, with results in a dialog.

## Round 3: external-browser edge cases

- Canonicalize confined guest paths before creating document URIs, so dot segments
  and redundant separators cannot break external ancestry resolution.
- Resolve missing picker MIME types on IO before opening the selected document.
  Do not block the main thread on an in-process provider that needs service admission.

## Verification limits and phone checklist

Host tests cover row migration/serialization, ancestry confinement/canonical paths
and inherited start-directory fallback. Android UI regressions are compiled; they
are not executed locally. No emulator was launched. Neither review nor successful
compilation establishes Android file-manager compatibility or measured frame timing.

On the new Preview: delete keys from either row, collapse/expand and rotate;
restore settings while selecting a key or editing a command; navigate Settings
without blank flashes; inspect Android HOME and Linux HOME/start paths; refresh
inherited directories; browse a folder externally from a cold app, including dot
paths; open text/image/PDF files from the fallback picker; test Arabic, large text,
IME-open controls, dragging and reduced motion. Package operations still require
review and session exclusion. External browsing is read-only.
