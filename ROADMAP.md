# sbt-splice

An sbt 2 / Scala 3 plugin (`rocks.earlyeffect` % `sbt-splice`) that private-links Scala.js IR (remapping `@JSImport` to `Global(__splice_*)` when you have mapped libraries), wraps pinned vendor JS onto those globals, and writes a browser-loadable script. Zero Node: never invoke npm, npx, node, or read a `package.json`. JS libraries, when used, arrive as **pinned bytes**: a vendored file, a Maven/WebJar coordinate, or a fetch from a CDN / trusted repo that downloads the `.js` and nothing else.

This is a **general-purpose** Scala.js tool. Mapped `@JSImport("some-lib")` (or CommonJS `require`) without npm is in scope. So is a Scala-only client with empty `spliceLibs`: `spliceFull` is the Node-free production bundle. It is not a Preact plugin, not an ascent plugin, and not a Specular plugin.

First consumers happen to be in this org:

- **preactile** is the demanding one: `@JSImport("preact")` currently forces `npm install` plus a Vite build in `specularJsLink`.
- **ascent examples** often have no npm imports but still use Vite as a file server.
  Dev loop is spliceFast plus [ascent#52](https://github.com/early-effect/ascent/issues/52)
  preview (serve the tree, SSE full reload). Publish is one `spliceFull` script.

GitHub: `early-effect/sbt-splice`. Local: `~/projects/fun/sbt-splice`. Coordinate: `rocks.earlyeffect` % `sbt-splice` (`_sbt2_3`).

**Status legend:** done · in progress · not started

| Phase | What ships | Status |
|---|---|---|
| 0 | sbt 2 plugin skeleton, publish identity, empty task | done |
| 1 | File-mapped specifiers after `fastLinkJS`; unresolved import fails | done |
| 2 | Resolvers + Coursier cache: Maven/WebJar, jsDelivr, unpkg | done |
| 3 | Full optimize via Scala.js minify + post-link Closure; size budget | done |
| 4 | Scripted `@JSImport` of a vendored library runs without Node | done |

This file is forward-looking. Git history records what shipped.

**Pre-release hardening is in progress.** Internals of phases 0–4 work; Central
publish waits until the waves below are done. First consumers (preactile, then
an ascent example) still follow publish. See §6.

| Wave | What | Status |
|---|---|---|
| rename | Plugin lives in `rocks.earlyeffect.splice` (no `sbt` package segment) | done |
| modules | ESM / CJS / UMD / global wrap; `.extern` Closure hatch | done |
| ir | Private link; `@JSImport` → Global in IR; no linker-JS regex rewrite | done |
| maps | Configurable source maps (fast on, full off by default) | done |
| github | Tag tarball resolver, sha256 pin | done |

## Stack and style

**sbt 2 only. Scala 3 only.** No sbt 1 artifact, no Scala 2. The published plugin is `_sbt2_3`. `project/build.properties` is sbt 2.x; `scalaVersion` is Scala 3.8. Do not keep a 1.x code path "for compatibility."

| Piece | Role |
|---|---|
| **zipx** | This repo's CI. Generated `.github/workflows/ci.yml`, `zipxWorkflowCheck`, `ZipxCentral.release`, fmt gate, `testFull` + `scripted`. No hand-written `release.yml`. |
| **Specular** | This repo's docs site (tests-as-docs), Pages via `ZipxDocs.pages`. Distinct from splice being a Specular plugin: it is not. Specular is how we document splice. |
| **ZIO** | Effectful core: resolve, fetch, splice, Closure. Easy to maintain and reason about (`ZIO` / `ZLayer`, typed errors, no hidden `try`/`Await`). The sbt AutoPlugin is a thin `Task` wrapper that runs that program. |
| **zio-test** | Unit and property tests for the core. Scripted tests cover the sbt boundary. |

Write the interesting logic in ZIO. Do not dump a procedural script into `build.sbt`. Failures are values until the plugin boundary, where they become sbt task failures.

## 1. Goal and non-goals

**Goal.** splice private-links Scala.js IR and emits a browser-loadable artifact. Mapped `@JSImport` becomes `Global(__splice_*)` in IR; pinned vendor files wrap onto those globals. Empty `spliceLibs` is valid: there is nothing to wrap, and `spliceFull` is still the production file. Development (`spliceFast`) is seconds and readable enough. Production (`spliceFull`) is small and efficient. The output is suitable as a Specular `assets/client.js` or any other static `<script>` the consumer already serves.

**Non-goals.**

- Do not reimplement the Scala.js linker. Reuse its linker (same config as `fastLinkJS` / `fullLinkJS`) on remapped IR. Do not consume or rewrite vanilla linker JS.
- Do not invoke npm, npx, node, or read a `package.json`. No Vite wrapper. No esbuild, terser, swc, or rolldown. Fetching JS is GET-bytes only: no install scripts, no registry metadata, nothing to execute.
- Do not rewrite imports to live CDNs (that is scalajs-importmap; not a sealed supply chain). Build-time fetch of a **pinned** file into the Coursier cache is fine; leaving `import "https://cdn…"` in the output is not.
- Do not become a general JS application bundler (no npm graph, no `"exports"` walk, no Node builtins). Specifiers not in the map fail. Nested relative imports inside a mapped file are resolved against that file.
- Do not replace a static file server or live-reload. That is ascent's preview server (ascent#52) or Specular `DocsServe`. Splice writes a file; preview serves the tree.
- Do not support sbt 1.x or Scala 2. This plugin is sbt 2 + Scala 3 only.

## 2. Constraints

- **sbt 2 + Scala 3.8, early-semver.** Same publish identity as sbt-zipx / sbt-specular: `organization := "rocks.earlyeffect"`, `organizationName := "Early Effect"`, `versionScheme := Some("early-semver")`, zipx-generated CI (`ZipxCentral.release`, `ZipxDocs.pages`), `usePgpKeyHex(sys.env.getOrElse("PGP_KEY_HEX", "MISSING_KEY_HEX"))`. Publishing is CI-only. No hand-written `release.yml`.
- **JDK 21+ to run `spliceFull`.** Our Closure pin (`v20260726`) requires Java 21. This repo's zipx image is JDK 25. `spliceFast` does not run Closure.
- **Zero Node.** The plugin and its tests never spawn Node. Scripted tests must pass with Node absent from `PATH`. No jsdom, no Playwright, no `node_modules` in this repo.
- **Sealed JS.** Every spliced byte is pinned: a file in the repo, a Maven/WebJar checksum, or a CDN fetch with a content hash. Fail loud on unresolved specifiers. Never leave `import "foo"` in the output. Never run anything obtained from the fetch.
- **Do not invent a minifier.** Reuse Scala.js minify for the Scala graph. The follow-up JS minifier is our Closure Compiler JAR (`com.google.javascript` % `closure-compiler`, pin in `ZipxVersions`). Details in §4.

## 3. Architecture

```
Compile / scalaJSIR
        │
        ▼
  remap IR             mapped @JSImport → Global(__splice_*)
        │
        ▼
  private Linker.link  same scalaJSLinker as fast / full, private dir
        │              vanilla fastLinkJS / fullLinkJS stay untouched
        ▼
  resolve / wrap       specifier → File (Coursier cache or vendor)
        │              vendor IIFE onto __splice_*; leftover check
        ▼
  optimize             fast: none (readable enough)
                       full: Scala.js minify (already in the private full link)
                             + Closure advanced on the combined file
        │
        ▼
  emit                 one browser-loadable file (default),
                       path configurable
```

The splice task reads `scalaJSIR` and the Scala.js linker configured for `fastLinkJS` / `fullLinkJS`. It remaps mapped `@JSImport` load specs, then calls `Linker.link` into `target/splice/fast-link` or `full-link`. It does not rewrite vanilla linker output and does not hook `scalaJSIR` globally, so `fastLinkJS` stays an import-based ES module.

**Plugin shape (sketch).**

- `spliceFast` private-links remapped IR with the fast linker config. `spliceFull` does the same with the full-opt linker, then Closure. Names match Scala.js (`fast` / `full`) and the two-stage feel of scalajs-bundler.
- Bare specifiers (`"foo"`, `"foo/plugin"`) map to a **source** that resolves to a File. The splice step only ever sees files. Several mappings in one project are the normal case, not a special case.
- Fail the task on the first unresolved bare specifier (message names the specifier and the file that referenced it). After emit, a leftover `from "foo"` or `require("foo")` is a bug.

### How JS arrives (Coursier cache, resolver-style)

Pulling in a pinned dep should feel like `resolvers` + `libraryDependencies`. Fetch and cache through **sbt's Coursier** (`csrCacheDirectory`, same `~/.cache/coursier` / `~/Library/Caches/Coursier` already used for Scala jars). Do not invent a parallel cache. Offline works when the Coursier cache is warm (`CachePolicy.LocalOnly` / sbt offline), same as any other dependency.

**Public shape (sketch, not code):**

```text
spliceResolvers += Splice.jsDelivr
spliceResolvers += Splice.unpkg
spliceResolvers += Splice.github
# Maven/WebJars already see the project's resolvers (Central, etc.)

spliceLibs += Splice.lib("foo", "1.2.3", "dist/foo.module.js")
                 .sha256("…")              // required for CDN; Maven uses repo checksums
spliceLibs += Splice.webjar("foo", "1.2.3", "dist/foo.module.js")
spliceLibs += Splice.file("foo", baseDirectory.value / "vendor/foo.module.js")
spliceLibs += Splice.github("foo", "owner/repo", "1.2.3", "dist/foo.js").sha256("…")
```

The string `"foo"` is the bare specifier `@JSImport` uses. Version + path pick the file. The same shape is `"preact"` / `"htm"` / `"lit"` / anything else. Resolver list is search order, like Ivy: first hit that verifies wins. A project that must not talk to CDNs omits `Splice.jsDelivr` / `Splice.unpkg` and keeps WebJars + vendor files.

| Source | Coordinate | Pin | Cache |
|---|---|---|---|
| **Vendor** | `File` in the repo | the file itself (git) | none |
| **Maven / WebJar** | `ModuleID` + path inside the jar (`org.webjars.npm` % `{name}`) | Maven checksums | Coursier `update` in a dedicated `Splice` config (not on the Compile classpath) |
| **CDN** | package + version + path, expanded by a resolver | **sha256 required** (jsDelivr/unpkg do not ship Maven `.sha256` files) | Coursier `FileCache` keyed by the expanded HTTPS URL (`CACHE/https/cdn.jsdelivr.net/…`) |
| **GitHub** | `owner/repo` + exact tag + path inside the tag tarball | **sha256 required** (the tarball) | Coursier `FileCache` of `archive/refs/tags/{tag}.tar.gz`; extract one path after stripping the root dir |

Built-in resolvers expand to GET-able URLs. Defaults we should ship because they host published package files **as-is** (no rewrite/bundle):

- **jsDelivr:** `https://cdn.jsdelivr.net/npm/{name}@{version}/{path}`
- **unpkg:** `https://unpkg.com/{name}@{version}/{path}`
- **WebJars / Maven:** existing `resolvers`, artifact `org.webjars.npm` % `{name}` % `{version}`, then the path under `META-INF/resources/webjars/…`
- **GitHub:** `https://github.com/{owner}/{repo}/archive/refs/tags/{tag}.tar.gz` (opt-in via `Splice.github`). A 404 retries the `v`-prefixed tag. Hash mismatch fails immediately; do not fall across CDNs.

**Do not default esm.sh** or other CDNs that rewrite/bundle. We want the file the package published, not a transformed module graph.

**Use Coursier, not a home-grown downloader.** sbt 2 already depends on lm-coursier. WebJars go through the normal `update` graph. CDN URLs go through `coursier.cache.FileCache` with the checksum on the `Artifact` (do not rely on `ModuleID.from(url)` as the public API: Coursier has historically ignored or deprioritized `from`, see sbt#5418). A dedicated `Splice` configuration keeps `.js` / WebJar jars off `Compile` / `Test` classpaths.

**Fetch-only rules** (refuse, do not "helpfully" do these):

- Do not call the npm registry, `npm pack`, or `npm install`.
- Do not read `package.json` to discover `"main"` / `"module"` / `"exports"`.
- Do not run postinstall, lifecycle scripts, or any JS obtained from the fetch.
- Do not clone a repo and build it.
- Do not rewrite the Scala.js output to point at a live CDN. Fetch at **build** time, splice the bytes, emit a self-contained file.
- Do not use a floating tag (`@1`, `@latest`) or a branch. GitHub tags must be exact. Missing sha256 on a CDN or GitHub coord fails the task. Hash mismatch fails the task.

**Resolve.** Walk `import` / `export from` (and `require()` if the link was CommonJS). Look up each bare specifier in the map. Recurse into the file's own relative imports (`./plugin.js`). Relative paths inside a vendor file are resolved against that file, not against the map. If a remote fetch's file imports another bare specifier, that specifier needs its own map entry.

**Emit.** Default is one file, so a `<script>` or static asset just works. `spliceFast` may later emit a small directory (prelude plus linker chunks) so a preview server can HTTP-cache unchanged modules after a full reload. `spliceFull` is one script.

## 4. Optimization design

Two different tools, two different jobs. Scala.js minify shrinks the Scala graph. Closure is the production minifier for the **printed** spliced file (vendor wrappers plus that minify output). It is not a substitute for the Scala.js linker, and the linker is not a substitute for minifying vendor files.

| Task | On top of | Intent |
|---|---|---|
| `spliceFast` | private remapped link (fast linker config) | Development, seconds, readable enough |
| `spliceFull` | private remapped link (full linker config) + Closure | Production, small and efficient |

### What Scala.js already does (reuse this)

`fullLinkJS` is the Scala graph optimizer. Do not reimplement it.

| Piece | What it sees | What splice does |
|---|---|---|
| Linker (`fastLinkJS` / `fullLinkJS`) | `.sjsir` only | splice calls `Linker.link` on remapped IR into a private directory. Vanilla linker tasks stay unchanged. Extra JS still cannot enter the linker. |
| IR optimizer | Scala.js IR | Comes with the link. Leave it on. |
| Minify (Scala.js 1.16+, on in `fullLinkJS`) | Property names of **Scala classes**, using types and Scala.js semantics | Keep on. This is the production shrink of the Scala graph, including under `ModuleKind.ESModule`. |
| Closure backend (`ClosureLinkerBackend`) | Emitter trees of the Scala graph, one module, **not** `ESModule` | Do not rely on this. Deprecated and off by default as of Scala.js 1.21. Our newer Closure JAR evicts `scalajs-linker`'s `v20220202`, so `withClosureCompiler(true)` may not load while splice is on the classpath. Unsupported if it breaks. |

### Extra JS cannot enter the linker

The public `Linker.link` takes `Seq[IRFile]`, module initializers, an output directory, and a logger. `injectedIRFiles` are also IR (CoreJSLib and friends from the emitter). There is no `additionalJSFiles` on the public linker API.

`jsHeader` is prepended text, not a library input. `ClosureLinkerBackend.buildChunk` transforms Scala.js emitter trees into one `JSChunk` and requires `moduleKind != ESModule` and `modules.size <= 1`. Wrapping a `.js` file as IR is not a supported path.

So extra JS cannot be fed into the Scala.js linker as additional inputs. Linker GCC, even if someone re-enabled it, never sees spliced files. The production path is a **post-link Closure pass** on the spliced file. We invoke `com.google.javascript` % `closure-compiler` ourselves. The pin is **ours** (`v20260726` in `ZipxVersions`), not Scala.js's. We do not fork `ClosureLinkerBackend`. When Scala.js removes Closure from the linker, splice's post-link pass stays.

Scala.js 1.21's guidance is to follow `fullLinkJS` with a JavaScript minifier (they suggest Vite / Rolldown). Splice's constraint is no Node, so that follow-up is JVM Closure. Minify plus a general minifier is the pipeline; we already ran minify in the private full link.

Post-link Closure **parses** printed minify JS. That is a different input than Scala.js GCC, which consumed emitter trees. Scala.js encodes a real `_` in a Java name as U+FF3F; the minify printer emits `\uff3f` in identifiers. Closure still rejects U+FF3F ([closure-compiler#2851](https://github.com/google/closure-compiler/issues/2851)). Before compile we rewrite `\uff3f` / `\uFF3F` / U+FF3F to `$uFF3F`. Advanced mode then renames the identifier.

Host free-vars: Scala.js `js.Dynamic.global.X` is a free variable `X`. Two buckets:

- **Browser host APIs** (`onmessage`, `attachEvent`, `setTimeout`, …). Builtin Window externs treat those names as properties. `BrowserExterns` declares them as `var`s so the output still calls the real host. Do not silence `UNDEFINED_VARIABLES`; that would let Closure rename `onmessage`.
- **Node-shaped names that are not on the browser host** (`process` today). These are `NodeStubs`: compiler **inputs**, not externs. A `var process;` extern folds `typeof process !== "undefined"` to true and leaves host `process.env` / `process.exitCode`, which throw in a browser. The stub is a small object (`env`, `exitCode`, `browser`); Closure may rename the binding. Unused stubs DCE away. Unknown free-vars still fail. Do not auto-stub Closure errors: a `typeof Buffer !== "undefined"` probe must stay false in the browser.

`spliceFull` needs JDK 21+ (current Closure's floor).

### ESModule limitation (load-bearing)

From the [Scala.js module docs](https://www.scala-js.org/doc/project/module.html): when using ECMAScript modules, `fullLinkJS` optimizations are limited because Closure cannot be used with them. The backend hard-fails: `Cannot use module kind ESModule with the Closure Compiler`. Issue [#3893](https://github.com/scala-js/scala-js/issues/3893) is closed won't-fix; Closure was deprecated in all configs in 1.21 ([#5244](https://github.com/scala-js/scala-js/issues/5244), shipped in [1.21.0](https://www.scala-js.org/news/2026/04/04/announcing-scalajs-1.21.0/)).

`@JSImport("foo")` requires `ModuleKind.ESModule` or `CommonJSModule`. The linker refuses `@JSImport` under `NoModule` unless the facade has a `globalFallback`. That is the usual pattern for Scala.js facades of npm libraries. `NoModule` is the natural kind for Scala-only apps (empty `spliceLibs`). It is not a viable default for splice consumers that use `@JSImport`.

### Default

Private full link (Scala.js minify on) plus our post-link Closure pass on one compilation unit. Emit a classic script, not an ES module.

- **`spliceFast`:** private remapped link with the consumer's module kind (`ESModule` when `@JSImport` is in play; `NoModule` is fine for Scala-only). Wrap vendor files if any. No Closure. Readable, seconds.
- **`spliceFull`:** the same with the full-opt linker, then Closure advanced. Empty `spliceLibs` still runs that pass.

Do not re-enable linker GCC as a production path. Extra JS cannot enter the linker; our JAR may evict the one `ClosureLinkerBackend` was compiled against.

Terser, esbuild, swc, and Rolldown all imply Node or a native binary we will not add. Concatenating unminified vendor files onto `fullLinkJS` output is not enough.

### How spliced files participate in minification / DCE

After splice, there are no bare specifiers. Scala.js call sites and every mapped library body are one JS program.

Feed each spliced file to Closure as **inputs**, not externs. Unused exports can be dropped. Used names are renamed together with the Scala.js call sites.

Externs are for the **browser host** and for names Scala.js already protects (`constructor`, `toString`, `$classData`, `length`, `call`, `apply`, `NaN`, `Infinity`, `undefined`; DOM globals). Mirror the spirit of `ClosureLinkerBackend.ScalaJSExterns`. `BrowserExterns` also declares host **free-vars** (`onmessage`, `attachEvent`, `postMessage`, …) that `js.Dynamic.global` compiles to; Window externs only have those as properties. Node-shaped free-vars (`process`) are `NodeStubs` inputs, not externs. Do not extern a library's public API unless something *outside* the spliced file must call it by a stable name (an HTML inline script). Typical `@JSImport` consumers do not need that.

If a spliced library is written in a style advanced mode will miscompile, fail the task with the Closure error. Do not silently fall back to concat. Escape hatch: mark a specifier as `extern` (include as a file, don't let Closure rename it) and optionally a conservative Closure `SIMPLE` / whitespace pass on that chunk. That is a last resort and it will miss the size budget. Size budget in Phase 3 decides whether advanced-on-combined is viable; if a real library's shape is too hostile, document the fallback in that PR rather than baking it in now.

### Size budget

Phase 3 is not done until `spliceFull` output is measurably smaller than unminified concat of `fullLinkJS` plus the same vendor files. Record both byte sizes in the scripted test. Gzip/brotli is informative; uncompressed is the gate. The fixture should be a real published library (or several), not a three-line stub, so DCE has something to do.

### Caching: inputs yes, per-dep Closure output no

Coursier caches **fetched** JS: the file from WebJars or jsDelivr. That is independent of your Scala.js program and is the right grain.

Do **not** cache "minified `$lib`" after Closure as a reusable artifact. `spliceFull` runs Closure on **one compilation unit**: Scala.js output plus every spliced file together. Advanced mode rename and DCE see each library *through your call sites*. If the Scala graph stops calling a function, Closure can drop more of that library. If you add a second specifier, the combined graph changes. A previously minified blob is then wrong: either names no longer match the Scala.js side, or you kept unused code.

Several JS deps make this sharper, not weaker. Five vendor files are still one Closure program. Minifying each, caching each, then concatenating is the unminified-concat failure mode with extra steps. Independent advanced-mode runs also pick independent rename maps, so a name in the library and the same name at the Scala.js call site would not agree unless you extern the whole public API, which throws away DCE.

What *does* make sense:

- **Coursier:** raw vendor bytes, keyed by URL / Maven coord + checksum. Shared across projects.
- **sbt task cache on `spliceFull`:** skip Closure when *all* of these are unchanged: private full-link digest, every spliced file digest, Closure JAR version, externs, splice settings. That is the combined artifact in `target/`, not a Coursier entry. A Scala-only edit still re-runs Closure (correct). A no-op rebuild does not.
- **`spliceFast`:** concat/rewrite is cheap; task cache is enough. No Closure.

The only per-dep minify worth caching would be a `SIMPLE` / whitespace pass on a library marked `extern`. That is the escape hatch, not the default. Do not build the cache around it.

## 5. Phases

Each phase is independently shippable: compiles, tests, can be published.

### Phase 0: skeleton

Checkable:

- [x] sbt 2.x only (`project/build.properties`), Scala 3.8 only, `SbtPlugin`. No sbt 1 / Scala 2 cross.
- [x] `organization := "rocks.earlyeffect"`, `organizationName := "Early Effect"`, Apache-2.0, `versionScheme := Some("early-semver")`
- [x] `homepage` / `scmInfo` point at `github.com/early-effect/sbt-splice`
- [x] zipx: latest `sbt-zipx` from Central, `zipxJavaVersion := JdkVersion("25")`, parallel Verify (`fmt` / `workflow-check` / `advisories` / `test` with `testFull` and `scripted`), `ZipxCentral.release`, `ZipxDocs.pages`
- [x] Specular docs module (plugin artifact kind), theme like other early-effect plugins
- [x] ZIO core + zio-test; AutoPlugin is a thin wrapper. At least one zio-test suite in Phase 0 (even if it only asserts the empty program's shape)
- [x] `usePgpKeyHex(sys.env.getOrElse("PGP_KEY_HEX", "MISSING_KEY_HEX"))`, `publishTo` Central Portal (`localStaging` / snapshots)
- [x] AutoPlugin that requires Scala.js, exposes `spliceFast` / `spliceFull` depending on the linker; empty tasks fail with a clear "not implemented" or no-op copy until Phase 1
- [x] Scripted test: plugin loads, task runs, no Node involved
- [x] Repo in `early-effect` so org publish secrets inherit; no hand-written `release.yml`

No specifier resolution yet. No Closure yet.

### Phase 1: file-mapped specifiers after fastLinkJS

Checkable:

- [x] Setting: specifier → `File` (ESM or CJS on disk)
- [x] `spliceFast` rewrites or inlines those specifiers in `fastLinkJS` output
- [x] Unresolved bare specifier fails the task (message names specifier and referring file)
- [x] Output contains no leftover `from "foo"` / `require("foo")` for mapped names
- [x] Several specifiers in one project (two mapped files) splice together
- [x] Output path is configurable
- [x] Scripted: `@JSImport("foo")` plus a tiny vendored `foo.js`; output contains no leftover bare specifier for `foo`
- [x] Scripted: missing mapping fails
- [x] Scripted still has no Node on `PATH`. The plugin never spawns Node. CI Verify runs Chekhov Firefox via `e2e/chekhovInstall` (Node 24; browsers under `target/ms-playwright`)

### Phase 2: resolvers + Coursier cache (Maven / WebJar / CDN)

Checkable:

- [x] `spliceResolvers` is configurable like `resolvers`. Built-ins: WebJars/Maven (project `resolvers`), jsDelivr, unpkg. User can add, remove, reorder. No esm.sh by default.
- [x] `spliceLibs` maps a bare specifier to a coordinate: vendor `File`, or `{name, version, path}` plus optional `sha256`. WebJar form uses `ModuleID`.
- [x] Maven/WebJar: `update` in a dedicated `Splice` configuration via Coursier. Jar is not on the Compile classpath. Extract the named `.js`. Fail if the jar has no matching path.
- [x] CDN: expand through the resolver to an HTTPS URL, download with `coursier.cache.FileCache` into `csrCacheDirectory`, verify sha256. Missing pin or mismatch fails.
- [x] Cache hit does not re-fetch. Offline / `CachePolicy.LocalOnly` succeeds when Coursier already has the file (or a vendor path is used).
- [x] Scripted: WebJar-style test jar; jsDelivr-shaped `httptest` with correct hash; wrong hash fails; resolver omitted → not found.
- [x] No npm, no `package.json`, no process spawn. No second cache directory of our own.

Vendor files from Phase 1 stay the zero-network path. Remotes are opt-in via resolvers.

### Phase 3: full optimize

Checkable:

- [x] `spliceFull` depends on `fullLinkJS` (minify on)
- [x] After splice, run Closure advanced on the combined file via `com.google.javascript % closure-compiler` (version pinned in the plugin)

The Closure JAR later diverged from Scala.js (`v20260726`; see §4 and §7).
- [x] Default full artifact is one script
- [x] Each spliced file is a Closure input; browser/Scala.js names are externs
- [x] Size budget: output strictly smaller than unminified concat of the same `fullLinkJS` plus the same vendor file(s); numbers asserted in scripted
- [x] Closure errors fail the task
- [x] sbt task cache skips Closure when linker digest + vendor digests + Closure version + settings are unchanged
- [x] Still no Node

### Phase 4: a real `@JSImport` runs, no Node

Checkable:

- [x] Scripted app uses `@JSImport` against a **vendored** file of a real published library (not fetched by npm). A tiny synthetic `"foo"` is not enough here; Phase 1 already covers that. A small ESM library with a callable API is the point (Preact is a convenient fixture because preactile will use it; it is not the only library splice must work with).
- [x] `spliceFast` (and `spliceFull` if Phase 3 is in) produce output with no leftover bare specifier
- [x] A browser-shaped check **executes** the output against a tiny DOM or equivalent host the library needs
- [x] Engine is JVM-hosted. Prefer GraalJS (`org.graalvm.polyglot`) plus a tiny HTML fixture or a minimal `document` shim. HtmlUnit is a fallback. Not Rhino (gone from Scala.js 1.x). Not Node, jsdom, or Playwright.
- [x] Scala.js's `scalajs-js-envs-test-kit` is for testing `JSEnv` implementations; the default `JSEnv` is Node, so it is not the Phase 4 runner. Do not add a Node `JSEnv` to make the testkit green.
- [x] Scripted CI job does not install Node; `PATH` without `node` still passes

Static "no bare import" assertions from Phase 1 stay. Phase 4 adds a run.

GraalJS is this repo's run proof (unit + scripted). It is **not** a plugin feature.
Keep it off the published classpath (`% Test` here; the scripted meta-build is not
published). `pomOnly()` on `org.graalvm.polyglot:js` does not pull `js-language`.

## 6. Next: pre-release, then publish, then adopt

The phase-0–4 internals work. Remaining **in this repo** is the pre-release
table at the top (all waves done). Central publish waits until you cut that
release. Consumers adopt from Central after that.

1. **Finish the pre-release waves** (rename, modules, IR, maps, and GitHub are done).
2. **First Central publish** of `rocks.earlyeffect` % `sbt-splice`. Until that
   exists, consumers cannot depend on it.
3. **preactile docs client.** `docs / specularJsLink` stops calling `npm install`
   and `npm run build`. It runs `docsClient / spliceFast` (dev) / `spliceFull`
   (publish) and copies the file to `target/site/assets/client.js`. Docs that
   currently say "npm install preact" and "Vite setup" get rewritten to a
   specifier map. Chekhov E2E against the served site still passes (that is
   preactile's browser check, not splice's).
4. **An ascent example with no npm imports** (e.g. `todo-conduit`). Drop Vite.
   The loop is below; do not copy the Vite `SmallModulesFor` snippet onto today's
   one-file `spliceFast`.

### Ascent developer loop (the DX we want)

Splice writes JS. It does not live-reload. [ascent#52](https://github.com/early-effect/ascent/issues/52)
preview serves the tree and SSE-reloads the tab when a stamp changes
(`location.reload()`, not Vite HMR). That is the whole "fast on the fly" story
we are aiming at: no npm, no `import.meta.hot`, a JVM file server plus a
rebuild stamp.

| Mode | JS | Linker split | Reload |
|---|---|---|---|
| **Dev (now)** | `~spliceFast` → one `fast.js` (FewestModules) | default | preview SSE, full reload, re-download the blob |
| **Dev (later)** | `~spliceFast` → directory: vendor prelude + linker chunks | `SmallModulesFor` on the **app** packages only | same SSE full reload; unchanged chunks can HTTP-cache |
| **Publish** | `spliceFull` → one Closure script | FewestModules | none |

Do **not** use `SmallestModules`. That style exists so Vite can HMR one class
file; there is no bundler here, and it would explode stdlib into dozens of
requests. Do **not** split `spliceFull`. Libraries in `spliceLibs` are pinned
bytes; they wrap once into the prelude and are not part of the incremental
loop.

Until directory emit exists, a multi-file private link must **fail the task**.
Silent concat of `import "./Foo$.js"` is how a copied Vite config ships a
broken `fast.js`.

Specular #55 is the adopt ticket on the docs-site side. Preactile adopts
sbt-splice on its own after publish. Ascent preview is the file server;
splice is not.

## 7. Decisions and leftovers

### Decided (do not reopen)

- **Fetch path.** WebJar: `update` in a hidden `Splice` config. CDN: Coursier
  `FileCache` into `csrCacheDirectory`. Not `ModuleID.from`.
- **CDN search.** A 404 may try the next enabled CDN. A hash mismatch fails
  immediately; do not fall across CDNs on a bad pin.
- **Module wrap.** ESM is rewritten onto `exports`. CJS and UMD run inside the
  same `module.exports` IIFE without that rewrite. AMD-only `define()`,
  `export * from`, and `import.meta` fail the task.
- **`.extern`.** Closure hatch only. Both tasks wrap and prepend (including a
  pure-global library). `spliceFull` does not pass that chunk as a Closure
  input; `__splice_*` is an extra extern so the rest of the program can call it.
- **Module shape.** `spliceFull` is one classic script. `spliceFast` may still
  look like ESM. Whether a production `<script type="module">` can load full is a
  preactile question, not a new splice phase.
- **Published ESM.** Real packages often put `export{x as h, ...}` on the same
  line as the bundle. `JsModules.rewriteExports` must handle that; leftover
  detection uses `leftoverExports`.
- **Closure.** JAR is `v20260726` (our pin in `ZipxVersions`, not Scala.js 1.22's `v20220202`). JDK 21+ to run `spliceFull`. Rewrite `\uff3f` / U+FF3F to `$uFF3F` before parse. Host free-vars in `BrowserExterns`; Node-shaped free-vars (`process`) in `NodeStubs` (compiler inputs, not externs). Fail the task on Closure errors. Linker GCC is not ours and may not load after eviction.
- **Allowlist.** `spliceResolvers` is the URL allowlist. A company adds an
  internal Maven repo of WebJars and drops public CDNs.
- **IR remap.** Mapped `@JSImport` becomes `Global(__splice_*)` in IR. splice
  tasks private-link remapped IR; they do not regex-rewrite Scala.js linker JS.
  Unmapped specifiers still fail leftover/unresolved checks. `scalajs-ir` and
  `scalajs-linker-interface` are explicit plugin dependencies (sbt-scalajs does
  not always export those types to Scala 3 sources).
- **Source maps.** `spliceFast / spliceSourceMaps` defaults on; full defaults
  off. Fast writes an indexed map whose first Scala.js section offset is the
  exact prepended wrapper line count, including blanks. Full, when enabled,
  asks Closure for a map. No vendor `.map` fetch.
- **GitHub tarballs.** `Splice.github("foo", "owner/repo", "1.2.3", "path")`
  plus `spliceResolvers += Splice.github`. sha256 pins the tarball. Extract
  one path after stripping the archive root dir. 404 may retry `v{tag}`; a
  hash mismatch fails immediately and does not search jsDelivr.
- **Split modules.** `spliceFull` is one file. `spliceFast` is one file until
  it grows a directory emit (prelude + linker chunks) for preview servers.
  Then `SmallModulesFor` on app packages is the ascent-dev split; never
  `SmallestModules`; never split full. Multi-file linker output must fail
  today's concat path. Live-reload is ascent preview (ascent#52), not splice.
  See §6.

### Still open

- **`spliceFast` directory emit.** Implementation leftover, not a product
  question. Needed before ascent should turn on `SmallModulesFor`. Fail-loud
  on multi-file linker output can ship first.
- **JS pin feed.** Opt-in `sbt-splice-zipx`, not a splice core feature. Catalog
  `Pin("splice", …)` vals are inventory; lookup fills version plus sha256; zipx
  rewrites those constructors; `materialize` keeps matching `spliceLibs` in
  sync. WebJar advisory-only; vendor files skipped. See the docs page
  "Keeping library pins current."

## 8. Sharp edges (this plugin)

Product-local rakes. Cross-plugin sbt 2 rakes live in the global `sbt-2-plugin`
rule.

- Plugin sources live in `rocks.earlyeffect.splice` (no `sbt` package segment).
- Do not hook `scalaJSIR` globally; that would poison vanilla `fastLinkJS`.
  Remap only inside the splice private link.
- `scalajs-ir` and `scalajs-linker-interface` must be explicit plugin
  dependencies. `addSbtPlugin("sbt-scalajs")` does not always export those
  types to Scala 3 plugin sources.
- `spliceFast` / `spliceFull` return `java.io.File`; wrap the task in
  `Def.uncached` (sbt 2 refuses `File` as a cached task output).
- sbt 2 `config("splice")` macro: the **val** must be capitalized
  (`val SpliceJs = config("splice").hide`).
- sbt 2 `target.value` is `target/out/jvm/…/<id>/`. Advertised output stays at
  `baseDirectory / "target" / "splice" / …`.
- Top-level `Unit` in a scripted `build.sbt` is not a `DslEntry`; wrap
  side-effecting helpers in a setting.
- Chekhov e2e is Firefox-only with a scoped `chekhovInstall` until
  [chekhov#24](https://github.com/early-effect/chekhov/issues/24) (aggregated
  install races apt across browsers).
- Closure pin is ours (`v20260726`) and evicts `scalajs-linker`'s `v20220202`.
  `spliceFull` needs JDK 21+. Do not feed minify output to Closure without the
  `\uff3f` rewrite.

## Prior art (none of these is splice)

| Tool | Why it is not splice |
|---|---|
| **[scalajs-bundler](https://scalacenter.github.io/scalajs-bundler/)** | webpack + npm. Two-stage feel (`fastOptJS` / `fullOptJS`) is the UX we copy. No sbt 2. |
| **[scalajs-esbuild](https://github.com/ptrdom/scalajs-esbuild)**, **[sbt-vite](https://github.com/johnhungerford/sbt-vite)**, **[sbt-jsbundler](https://github.com/johnhungerford/sbt-jsbundler)** | Node. |
| **[sbt-scalajs-importmap](https://github.com/armanbilge/scalajs-importmap)** | Rewrites `@JSImport` to CDN URLs. Not sealed. |
| **[sbt-jsdependencies](https://github.com/scala-js/jsdependencies)** | WebJars as global scripts, not ESM `@JSImport`. Officially not recommended for new projects. |
| **Scala.js `fullLinkJS` Closure** | Scala.js graph only. Bare specifiers stay broken. Off by default and deprecated in 1.21. Minify (1.16+) is the Scala-side piece we *do* reuse. |
