# Architecture boundary control plane

This directory is the machine-readable control plane for the approved Kiyori
refactor. It is separate from runtime source code and does not own application
state.

## Files

- `package-ownership.toml` maps every Kotlin/Java source file under
  `app/src/main/java` to one owner, records the sync zone, and declares
  package-dependency constraints for future `com.kiyori` roots. Planned
  capability, feature, and integration domains are file-exact package roots;
  an undeclared future domain fails as unmanaged. Vendored roots are also
  isolated from both Kiyori and Operit product packages.
- `manifest-components.txt` is the reviewed main-source-set Android Manifest
  contract snapshot. It preserves component classes plus action, category,
  authority, scheme, host, MIME type, process, and component permission
  multiplicity. M-01 has one explicit old/new application-class mapping.
- `debug-manifest-components.txt` separately pins the Debug-only exported QA
  receivers, actions, and their `android.permission.DUMP` guards. Keeping this
  contract separate makes moving any receiver back into main/release a gate
  failure.
- `manifest-structure-hashes.txt` and
  `debug-manifest-structure-hashes.txt` preserve the complete semantic
  Manifest trees for the main migration phases and Debug source set. XML
  formatting, attribute order, and sibling order are ignored, while hierarchy
  and every element/attribute value remain protected.
- `stable-identifiers.txt` records product, ecosystem, serialization, and
  external-identity literals with exact expected occurrence counts.
- `persistence-names.txt` records exact quoted DataStore, SharedPreferences,
  database, backup, and related persistence names with exact counts.
- `persistence-api-calls.txt` records every DataStore, SharedPreferences, Room,
  and WorkManager unique-work contract call with its source path, API, selected
  argument, and exact multiplicity. Formatting and comments do not affect it;
  aliases or direct imports that could bypass extraction are rejected. A new
  persistence creation API must first gain an explicit extractor and snapshot.
- `native-ipc-identifiers.txt` records exact native/JNI/AIDL package, exported
  JNI symbol, and every `System.loadLibrary` identifier with exact counts.
- `critical-file-hashes.txt` pins the complete bytes of AIDL contracts, Room
  schema/entity sources, ObjectBox UID/path contracts, persisted WorkManager
  worker/scheduler entrypoints, and backup/restore implementations after
  CRLF-to-LF normalization.

Counts are extracted only from Git-tracked files plus non-ignored untracked
source files under `app/`, `examples/`, and `tools/`. Ignored dependency,
generated, cache, and build trees do not affect the result, so a fresh clone
and an active development checkout evaluate the same contracts.

Snapshots are not a replacement for a migration design. Updating one is
allowed only when the corresponding contract change is in the approved
milestone, the formal document is updated first, and the diff explains the old
value, new value, and migration reason.

## Validation

Run the same checker locally and in CI:

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --require-main
```

The checker is read-only. It rejects unmanaged source files, package/path
mismatches, forbidden or out-of-layer imports and fully qualified project
references, Manifest drift, stable-contract count or critical-file hash drift,
tracked private/build artifacts, terminal changes, and M-01 changes outside its
exact candidate manifest. Dependency extraction masks comments and string
literals, and it does not treat package declarations as dependency edges.

## Exceptions

An exception is a temporary, file-exact record in `package-ownership.toml`.
It must contain a rule, a literal file path, a reason, an owner, and an
`expires_after` milestone beginning with `M-`. Wildcards, duplicate records,
and unused records fail the gate. Exceptions are debt registers, not permanent
allowlists.
