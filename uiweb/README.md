# MiniC UIWeb

`uiweb` is the TypeScript mirror of `src/main/java/minic/uilocal`.

UIWeb keeps the JavaFX workbench shape while talking to the Java UIAPI server over HTTP.
Local-only concerns such as editor layout, keyboard handling, themes, and browser storage
remain in TypeScript; compiler, runtime, observation, debug, realtime analysis, and
derived UI semantics are exposed through UIAPI adapters.

Default runtime target:

- `http://127.0.0.1:18080`

The HTTP adapter layer is intentionally handwritten and checked by `npm run verify:adapter-completeness`
so new UIAPI facade methods must be mirrored explicitly in TypeScript.

Every JavaFX UI source file under `uilocal` has one matching TS or TSX file under `uiweb/src`. Runtime resource files are mirrored under `uiweb/src/resources/minic/uilocal`.
