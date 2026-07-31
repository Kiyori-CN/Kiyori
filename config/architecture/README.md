# Architecture boundary control plane

This directory is the machine-readable control plane for the approved Kiyori
refactor. It is separate from runtime source code and does not own application
state.

## Files

- `package-ownership.toml` maps every Kotlin/Java source file under
  `app/src/main/java` to one owner, records the sync zone, and declares
  package-dependency constraints for future `com.kiyori` roots.
- `manifest-components.txt` is the reviewed Android component snapshot.
  M-01 has one explicit old/new application-class mapping.
- `stable-identifiers.txt` records product, ecosystem, serialization, and
  external-identity literals with exact expected occurrence counts.
- `persistence-names.txt` records exact quoted DataStore, SharedPreferences,
  database, backup, and related persistence names with exact counts.
- `native-ipc-identifiers.txt` records exact native/JNI/AIDL package and
  library identifiers with exact counts.

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
mismatches, forbidden or out-of-layer imports, Manifest drift, stable-contract
count drift, tracked private/build artifacts, terminal changes, and M-01
changes outside its exact candidate manifest.

## Exceptions

An exception is a temporary, file-exact record in `package-ownership.toml`.
It must contain a rule, a literal file path, a reason, an owner, and an
`expires_after` milestone beginning with `M-`. Wildcards, duplicate records,
and unused records fail the gate. Exceptions are debt registers, not permanent
allowlists.
