# MiniC UIWeb

`uiweb` is the TypeScript mirror of `src/main/java/minic/uilocal`.

This pass is intentionally local-only:

- no HTTP routes
- no WebSocket
- no generated API client
- no server-state synchronization

Every JavaFX UI source file under `uilocal` has one matching TS or TSX file under `uiweb/src`. Runtime resource files are mirrored under `uiweb/src/resources/minic/uilocal`.
