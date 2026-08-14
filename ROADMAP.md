# sbt-splice

An sbt 2 / Scala 3 plugin (`rocks.earlyeffect` % `sbt-splice`) that takes Scala.js linker output and produces browser-loadable JavaScript with bare module specifiers resolved. Zero Node: never invoke npm, npx, node, or read a `package.json`. JS libraries arrive as **pinned bytes**: a vendored file, a Maven/WebJar coordinate, or a fetch from a CDN / trusted repo that downloads the `.js` and nothing else.

This is a **general-purpose** Scala.js tool. Any project that uses `@JSImport("some-lib")` (or CommonJS `require`) and does not want npm in the build is in scope: one library or several, ESM or CJS files, mapped specifier to path. It is not a Preact plugin, not an ascent plugin, and not a Specular plugin.

First consumers happen to be in this org:

- **preactile** is the demanding one: `@JSImport("preact")` currently forces `npm install` plus a Vite build in `specularJsLink`.
- **ascent examples** often have no npm imports but still use Vite as a file server; splice should still emit a single (or small) JS file they can serve.

GitHub: `early-effect/sbt-splice`. Local: `~/projects/fun/sbt-splice`. Coordinate: `rocks.earlyeffect` % `sbt-splice` (`_sbt2_3`).

**Status legend:** done · in progress · not started

| Phase | What ships | Status |
|---|---|---|
| 0 | sbt 2 plugin skeleton, publish identity, empty task | not started |
| 1 | File-mapped specifiers after `fastLinkJS`; unresolved import fails | not started |
| 2 | Resolvers + Coursier cache: Maven/WebJar, jsDelivr, unpkg | not started |
| 3 | Full optimize via Scala.js minify + post-link Closure; size budget | not started |
| 4 | Scripted `@JSImport` of a vendored library runs without Node | not started |

This file is forward-looking. Git history records what shipped.

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

**Goal.** After Scala.js links, splice turns bare specifiers (`import * as $i_foo from "foo"`) into a browser-loadable artifact. Development (`spliceFast`) is seconds and readable enough. Production (`spliceFull`) is small and efficient. The output is suitable as a Specular `assets/client.js` or any other static `<script>` the consumer already serves.

**Non-goals.**

- Do not reimplement the Scala.js linker. Depend on `fastLinkJS` / `fullLinkJS`.
- Do not invoke npm, npx, node, or read a `package.json`. No Vite wrapper. No esbuild, terser, swc, or rolldown. Fetching JS is GET-bytes only: no install scripts, no registry metadata, nothing to execute.
- Do not rewrite imports to live CDNs (that is scalajs-importmap; not a sealed supply chain). Build-time fetch of a **pinned** file into the Coursier cache is fine; leaving `import "https://cdn…"` in the output is not.
- Do not become a general JS application bundler (no npm graph, no `"exports"` walk, no Node builtins). Specifiers not in the map fail. Nested relative imports inside a mapped file are resolved against that file.
- Do not replace a static file server or live-reload. That is ascent's preview server (ascent#52) or Specular `DocsServe`. Splice writes a file; preview serves the tree.
- Do not support sbt 1.x or Scala 2. This plugin is sbt 2 + Scala 3 only.

## 2. Constraints

- **sbt 2 + Scala 3.8, early-semver.** Same publish identity as sbt-zipx / sbt-specular: `organization := "rocks.earlyeffect"`, `organizationName := "Early Effect"`, `versionScheme := Some("early-semver")`, zipx-generated CI (`ZipxCentral.release`, `ZipxDocs.pages`), `usePgpKeyHex(sys.env.getOrElse("PGP_KEY_HEX", "MISSING_KEY_HEX"))`. Publishing is CI-only. No hand-written `release.yml`.
- **Zero Node.** The plugin and its tests never spawn Node. Scripted tests must pass with Node absent from `PATH`. No jsdom, no Playwright, no `node_modules` in this repo.
- **Sealed JS.** Every spliced byte is pinned: a file in the repo, a Maven/WebJar checksum, or a CDN fetch with a content hash. Fail loud on unresolved specifiers. Never leave `import "foo"` in the output. Never run anything obtained from the fetch.
- **Do not invent a minifier.** Reuse Scala.js for the Scala graph. For spliced libraries, use the same Closure Compiler JAR Scala.js uses. Details in §4.

## 3. Architecture

```
fastLinkJS / fullLinkJS
        │
        ▼
  resolve / splice     specifier → File (Coursier cache or vendor)
        │              vendor | Maven/WebJar | CDN via spliceResolvers
        ▼
  optimize             fast: none (readable enough)
                       full: Scala.js minify (already in fullLinkJS)
                             + post-link Closure on the combined file
        │
        ▼
  emit                 one browser-loadable file (default),
                       path configurable
```

The splice task depends on the linker task. It reads the linker `Report` and the JS files in `scalaJSLinkerOutputDirectory`. It does not call `Linker.link`.

**Plugin shape (sketch).**

- `spliceFast` (or `splice`) depends on `fastLinkJS`. `spliceFull` depends on `fullLinkJS`. Names match Scala.js (`fast` / `full`) and the two-stage feel of scalajs-bundler.
- Bare specifiers (`"foo"`, `"foo/plugin"`) map to a **source** that resolves to a File. The splice step only ever sees files. Several mappings in one project are the normal case, not a special case.
- Fail the task on the first unresolved bare specifier (message names the specifier and the file that referenced it). After emit, a leftover `from "foo"` or `require("foo")` is a bug.

### How JS arrives (Coursier cache, resolver-style)

Pulling in a pinned dep should feel like `resolvers` + `libraryDependencies`. Fetch and cache through **sbt's Coursier** (`csrCacheDirectory`, same `~/.cache/coursier` / `~/Library/Caches/Coursier` already used for Scala jars). Do not invent a parallel cache. Offline works when the Coursier cache is warm (`CachePolicy.LocalOnly` / sbt offline), same as any other dependency.

**Public shape (sketch, not code):**

```text
spliceResolvers += Splice.jsDelivr
spliceResolvers += Splice.unpkg
# Maven/WebJars already see the project's resolvers (Central, etc.)

spliceLibs += Splice.lib("foo", "1.2.3", "dist/foo.module.js")
                 .sha256("…")              // required for CDN; Maven uses repo checksums
spliceLibs += Splice.webjar("foo", "1.2.3", "dist/foo.module.js")
spliceLibs += Splice.file(baseDirectory.value / "vendor/foo.module.js")
```

The string `"foo"` is the bare specifier `@JSImport` uses. Version + path pick the file. The same shape is `"preact"` / `"htm"` / `"lit"` / anything else. Resolver list is search order, like Ivy: first hit that verifies wins. A project that must not talk to CDNs omits `Splice.jsDelivr` / `Splice.unpkg` and keeps WebJars + vendor files.

| Source | Coordinate | Pin | Cache |
|---|---|---|---|
| **Vendor** | `File` in the repo | the file itself (git) | none |
| **Maven / WebJar** | `ModuleID` + path inside the jar (`org.webjars.npm` % `{name}`) | Maven checksums | Coursier `update` in a dedicated `Splice` config (not on the Compile classpath) |
| **CDN** | package + version + path, expanded by a resolver | **sha256 required** (jsDelivr/unpkg do not ship Maven `.sha256` files) | Coursier `FileCache` keyed by the expanded HTTPS URL (`CACHE/https/cdn.jsdelivr.net/…`) |

Built-in resolvers expand to GET-able URLs. Defaults we should ship because they host published package files **as-is** (no rewrite/bundle):

- **jsDelivr:** `https://cdn.jsdelivr.net/npm/{name}@{version}/{path}`
- **unpkg:** `https://unpkg.com/{name}@{version}/{path}`
- **WebJars / Maven:** existing `resolvers`, artifact `org.webjars.npm` % `{name}` % `{version}`, then the path under `META-INF/resources/webjars/…`

GitHub Releases (a source tarball or `.tgz` on a tag) is an optional extra resolver: fetch the archive through Coursier, extract one path. Still bytes only.

**Do not default esm.sh** or other CDNs that rewrite/bundle. We want the file the package published, not a transformed module graph.

**Use Coursier, not a home-grown downloader.** sbt 2 already depends on lm-coursier. WebJars go through the normal `update` graph. CDN URLs go through `coursier.cache.FileCache` with the checksum on the `Artifact` (do not rely on `ModuleID.from(url)` as the public API: Coursier has historically ignored or deprioritized `from`, see sbt#5418). A dedicated `Splice` configuration keeps `.js` / WebJar jars off `Compile` / `Test` classpaths.

**Fetch-only rules** (refuse, do not "helpfully" do these):

- Do not call the npm registry, `npm pack`, or `npm install`.
- Do not read `package.json` to discover `"main"` / `"module"` / `"exports"`.
- Do not run postinstall, lifecycle scripts, or any JS obtained from the fetch.
- Do not clone a repo and build it.
- Do not rewrite the Scala.js output to point at a live CDN. Fetch at **build** time, splice the bytes, emit a self-contained file.
- Do not use a floating tag (`@1`, `@latest`). Version must be exact. Missing sha256 on a CDN coord fails the task. Hash mismatch fails the task.

**Resolve.** Walk `import` / `export from` (and `require()` if the link was CommonJS). Look up each bare specifier in the map. Recurse into the file's own relative imports (`./plugin.js`). Relative paths inside a vendor file are resolved against that file, not against the map. If a remote fetch's file imports another bare specifier, that specifier needs its own map entry.

**Emit.** Default is one file, so a `<script>` or static asset just works. A tiny set of files (rewritten relative imports, copied vendor files) is allowed for `spliceFast` if inlining is a measurable slowdown. `spliceFull` is one script.

## 4. Optimization design

Two different tools, two different jobs. Closure is the production minifier for **spliced** JS. It is not a substitute for the Scala.js linker, and the linker is not a substitute for minifying vendor files.

| Task | On top of | Intent |
|---|---|---|
| `spliceFast` | `fastLinkJS` | Development, seconds, readable enough |
| `spliceFull` | `fullLinkJS` | Production, small and efficient |

### What Scala.js already does (reuse this)

`fullLinkJS` is the Scala graph optimizer. Do not reimplement it.

| Piece | What it sees | What splice does |
|---|---|---|
| Linker (`fastLinkJS` / `fullLinkJS`) | `.sjsir` only | Depend on it. Never call `Linker.link`. |
| IR optimizer | Scala.js IR | Comes with the link. Leave it on. |
| Minify (Scala.js 1.16+, on in `fullLinkJS`) | Property names of **Scala classes**, using types and Scala.js semantics | Keep on. This is the production shrink of the Scala graph, including under `ModuleKind.ESModule`. |
| Closure backend (`ClosureLinkerBackend`) | Emitter trees of the Scala graph, one module, **not** `ESModule` | Do not rely on this for spliced JS. Deprecated and off by default as of Scala.js 1.21 (`withClosureCompiler(true)` still works, for now). |

### Extra JS cannot enter the linker

The public `Linker.link` takes `Seq[IRFile]`, module initializers, an output directory, and a logger. `injectedIRFiles` are also IR (CoreJSLib and friends from the emitter). There is no `additionalJSFiles` on the public linker API.

`jsHeader` is prepended text, not a library input. `ClosureLinkerBackend.buildChunk` transforms Scala.js emitter trees into one `JSChunk` and requires `moduleKind != ESModule` and `modules.size <= 1`. Wrapping a `.js` file as IR is not a supported path.

So extra JS cannot be fed into the Scala.js linker as additional inputs. Closure inside `fullLinkJS` never sees spliced files. The production plan is a **post-link Closure pass** on the spliced file, using the same compiler JAR Scala.js already depends on: `com.google.javascript % closure-compiler` (Scala.js 1.21 still pins `v20220202` from `scalajs-linker`). We invoke that JAR ourselves. We do not fork `ClosureLinkerBackend`. When Scala.js removes Closure from the linker, splice's post-link pass stays.

Scala.js 1.16's own guidance is that minify plus a general-purpose JS minifier gets within about 15% of historical Closure-on-Scala. Scala.js 1.21's guidance is to follow `fullLinkJS` with a JS minifier (they suggest Vite / Rolldown). Splice's constraint is no Node, so that follow-up is a JVM minifier.

### ESModule limitation (load-bearing)

From the [Scala.js module docs](https://www.scala-js.org/doc/project/module.html): when using ECMAScript modules, `fullLinkJS` optimizations are limited because Closure cannot be used with them. The backend hard-fails: `Cannot use module kind ESModule with the Closure Compiler`. Issue [#3893](https://github.com/scala-js/scala-js/issues/3893) is closed won't-fix; Closure was deprecated in all configs in 1.21 ([#5244](https://github.com/scala-js/scala-js/issues/5244), shipped in [1.21.0](https://www.scala-js.org/news/2026/04/04/announcing-scalajs-1.21.0/)).

`@JSImport("foo")` requires `ModuleKind.ESModule` or `CommonJSModule`. The linker refuses `@JSImport` under `NoModule` unless the facade has a `globalFallback`. That is the usual pattern for Scala.js facades of npm libraries. `NoModule` is therefore not a viable default for splice consumers that use `@JSImport`.

### Options and default

1. Link Scala.js as `NoModule` or CommonJS so `fullLinkJS` Closure-compiles the Scala graph, then splice vendored JS in, then optionally Closure the combined file with externs so each library's API survives advanced mode.
2. Splice first into one script, then run Closure on the whole program (need externs or annotations for the **host**, not for each library's internals).
3. Keep ESModule for fast; for full, emit a single Closure-optimized script.

**Default: 3, implemented as 2.**

- **`spliceFast`:** after `fastLinkJS`. Keep the consumer's module kind (typically ESModule, required for `@JSImport`). Resolve and splice. No Closure. Readable, seconds.
- **`spliceFull`:** after `fullLinkJS` (Scala.js minify already on). Splice into one script, then Closure advanced on that file as a **single compilation unit**. Emit a script, not an ES module, because Closure cannot consume ES modules the way Scala.js needs.

Option 1 is the weaker production path. Extra JS cannot enter the linker, so linker Closure (even if re-enabled on CommonJS) never sees spliced files. Concatenating after a Scala-only Closure pass leaves vendor JS unminified unless you Closure again. `NoModule` also cannot express `@JSImport`. One post-link pass on the spliced program is the honest path. Re-enable linker Closure only if a measured size win remains *after* the post-link pass; it is not the default.

Terser, esbuild, swc, and Rolldown all imply Node or a native binary we will not add. Concatenating unminified vendor files onto `fullLinkJS` output is not enough.

### How spliced files participate in minification / DCE

After splice, there are no bare specifiers. Scala.js call sites and every mapped library body are one JS program.

Feed each spliced file to Closure as **inputs**, not externs. Unused exports can be dropped. Used names are renamed together with the Scala.js call sites.

Externs are for the **browser host** and for names Scala.js already protects (`constructor`, `toString`, `$classData`, `length`, `call`, `apply`, `NaN`, `Infinity`, `undefined`; DOM globals). Mirror the spirit of `ClosureLinkerBackend.ScalaJSExterns`. Do not extern a library's public API unless something *outside* the spliced file must call it by a stable name (an HTML inline script). Typical `@JSImport` consumers do not need that.

If a spliced library is written in a style advanced mode will miscompile, fail the task with the Closure error. Do not silently fall back to concat. Escape hatch: mark a specifier as `extern` (include as a file, don't let Closure rename it) and optionally a conservative Closure `SIMPLE` / whitespace pass on that chunk. That is a last resort and it will miss the size budget. Size budget in Phase 3 decides whether advanced-on-combined is viable; if a real library's shape is too hostile, document the fallback in that PR rather than baking it in now.

### Size budget

Phase 3 is not done until `spliceFull` output is measurably smaller than unminified concat of `fullLinkJS` plus the same vendor files. Record both byte sizes in the scripted test. Gzip/brotli is informative; uncompressed is the gate. The fixture should be a real published library (or several), not a three-line stub, so DCE has something to do.

### Caching: inputs yes, per-dep Closure output no

Coursier caches **fetched** JS: the file from WebJars or jsDelivr. That is independent of your Scala.js program and is the right grain.

Do **not** cache "minified `$lib`" after Closure as a reusable artifact. `spliceFull` runs Closure on **one compilation unit**: Scala.js output plus every spliced file together. Advanced mode rename and DCE see each library *through your call sites*. If the Scala graph stops calling a function, Closure can drop more of that library. If you add a second specifier, the combined graph changes. A previously minified blob is then wrong: either names no longer match the Scala.js side, or you kept unused code.

Several JS deps make this sharper, not weaker. Five vendor files are still one Closure program. Minifying each, caching each, then concatenating is the unminified-concat failure mode with extra steps. Independent advanced-mode runs also pick independent rename maps, so a name in the library and the same name at the Scala.js call site would not agree unless you extern the whole public API, which throws away DCE.

What *does* make sense:

- **Coursier:** raw vendor bytes, keyed by URL / Maven coord + checksum. Shared across projects.
- **sbt task cache on `spliceFull`:** skip Closure when *all* of these are unchanged: `fullLinkJS` digest, every spliced file digest, Closure JAR version, externs, splice settings. That is the combined artifact in `target/`, not a Coursier entry. A Scala-only edit still re-runs Closure (correct). A no-op rebuild does not.
- **`spliceFast`:** concat/rewrite is cheap; task cache is enough. No Closure.

The only per-dep minify worth caching would be a `SIMPLE` / whitespace pass on a library marked `extern`. That is the escape hatch, not the default. Do not build the cache around it.

## 5. Phases

Each phase is independently shippable: compiles, tests, can be published.

### Phase 0: skeleton

Checkable:

- [ ] sbt 2.x only (`project/build.properties`), Scala 3.8 only, `SbtPlugin`. No sbt 1 / Scala 2 cross.
- [ ] `organization := "rocks.earlyeffect"`, `organizationName := "Early Effect"`, Apache-2.0, `versionScheme := Some("early-semver")`
- [ ] `homepage` / `scmInfo` point at `github.com/early-effect/sbt-splice`
- [ ] zipx: latest `sbt-zipx` from Central, `zipxJavaVersion := JdkVersion("25")`, fmt `once` + verify (`testFull` and `scripted`), `ZipxCentral.release`, `ZipxDocs.pages`
- [ ] Specular docs module (plugin artifact kind), theme like other early-effect plugins
- [ ] ZIO core + zio-test; AutoPlugin is a thin wrapper. At least one zio-test suite in Phase 0 (even if it only asserts the empty program's shape)
- [ ] `usePgpKeyHex(sys.env.getOrElse("PGP_KEY_HEX", "MISSING_KEY_HEX"))`, `publishTo` Central Portal (`localStaging` / snapshots)
- [ ] AutoPlugin that requires Scala.js, exposes `spliceFast` / `spliceFull` depending on the linker; empty tasks fail with a clear "not implemented" or no-op copy until Phase 1
- [ ] Scripted test: plugin loads, task runs, no Node involved
- [ ] Repo in `early-effect` so org publish secrets inherit; no hand-written `release.yml`

No specifier resolution yet. No Closure yet.

### Phase 1: file-mapped specifiers after fastLinkJS

Checkable:

- [ ] Setting: specifier → `File` (ESM or CJS on disk)
- [ ] `spliceFast` rewrites or inlines those specifiers in `fastLinkJS` output
- [ ] Unresolved bare specifier fails the task (message names specifier and referring file)
- [ ] Output contains no leftover `from "foo"` / `require("foo")` for mapped names
- [ ] Several specifiers in one project (two mapped files) splice together
- [ ] Output path is configurable
- [ ] Scripted: `@JSImport("foo")` plus a tiny vendored `foo.js`; output contains no leftover bare specifier for `foo`
- [ ] Scripted: missing mapping fails
- [ ] CI and scripted still have no Node on `PATH`

### Phase 2: resolvers + Coursier cache (Maven / WebJar / CDN)

Checkable:

- [ ] `spliceResolvers` is configurable like `resolvers`. Built-ins: WebJars/Maven (project `resolvers`), jsDelivr, unpkg. User can add, remove, reorder. No esm.sh by default.
- [ ] `spliceLibs` maps a bare specifier to a coordinate: vendor `File`, or `{name, version, path}` plus optional `sha256`. WebJar form uses `ModuleID`.
- [ ] Maven/WebJar: `update` in a dedicated `Splice` configuration via Coursier. Jar is not on the Compile classpath. Extract the named `.js`. Fail if the jar has no matching path.
- [ ] CDN: expand through the resolver to an HTTPS URL, download with `coursier.cache.FileCache` into `csrCacheDirectory`, verify sha256. Missing pin or mismatch fails.
- [ ] Cache hit does not re-fetch. Offline / `CachePolicy.LocalOnly` succeeds when Coursier already has the file (or a vendor path is used).
- [ ] Scripted: WebJar-style test jar; jsDelivr-shaped `httptest` with correct hash; wrong hash fails; resolver omitted → not found.
- [ ] No npm, no `package.json`, no process spawn. No second cache directory of our own.

Vendor files from Phase 1 stay the zero-network path. Remotes are opt-in via resolvers.

### Phase 3: full optimize

Checkable:

- [ ] `spliceFull` depends on `fullLinkJS` (minify on)
- [ ] After splice, run Closure advanced on the combined file via `com.google.javascript % closure-compiler` (same artifact Scala.js uses; version pinned in the plugin, documented if it diverges from Scala.js)
- [ ] Default full artifact is one script
- [ ] Each spliced file is a Closure input; browser/Scala.js names are externs
- [ ] Size budget: output strictly smaller than unminified concat of the same `fullLinkJS` plus the same vendor file(s); numbers asserted in scripted
- [ ] Closure errors fail the task
- [ ] sbt task cache skips Closure when linker digest + vendor digests + Closure version + settings are unchanged
- [ ] Still no Node

### Phase 4: a real `@JSImport` runs, no Node

Checkable:

- [ ] Scripted app uses `@JSImport` against a **vendored** file of a real published library (not fetched by npm). A tiny synthetic `"foo"` is not enough here; Phase 1 already covers that. A small ESM library with a callable API is the point (Preact is a convenient fixture because preactile will use it; it is not the only library splice must work with).
- [ ] `spliceFast` (and `spliceFull` if Phase 3 is in) produce output with no leftover bare specifier
- [ ] A browser-shaped check **executes** the output against a tiny DOM or equivalent host the library needs
- [ ] Engine is JVM-hosted. Prefer GraalJS (`org.graalvm.polyglot`) plus a tiny HTML fixture or a minimal `document` shim. HtmlUnit is a fallback. Not Rhino (gone from Scala.js 1.x). Not Node, jsdom, or Playwright.
- [ ] Scala.js's `scalajs-js-envs-test-kit` is for testing `JSEnv` implementations; the default `JSEnv` is Node, so it is not the Phase 4 runner. Do not add a Node `JSEnv` to make the testkit green.
- [ ] Scripted CI job does not install Node; `PATH` without `node` still passes

Static "no bare import" assertions from Phase 1 stay. Phase 4 adds a run.

## 6. Acceptance

The plugin is done when **any** Scala.js project can declare specifier → pinned JS, run `spliceFast` / `spliceFull`, and load the result in a browser with no npm in the loop. Neither of the first consumers is implemented in this repo; they adopt a published artifact.

First consumers (prove the general path, do not define the product):

1. **preactile docs client.** `docs / specularJsLink` stops calling `npm install` and `npm run build`. It runs `docsClient / spliceFast` (dev) / `spliceFull` (publish) and copies the file to `target/site/assets/client.js`. Docs that currently say "npm install preact" and "Vite setup" get rewritten to a specifier map. Chekhov E2E against the served site still passes (that is preactile's browser check, not splice's).
2. **An ascent example with no npm imports** (e.g. `todo-conduit`). Today Vite is only a file server. The example serves splice output as a static file (existing JVM server, Specular `DocsServe`, or ascent preview). No `npm run dev`, no `@scala-js/vite-plugin-scalajs` for that example.

Specular #55 is the adopt ticket on the docs-site side. Preactile adopts sbt-splice on its own after publish.

## 7. Open questions

- **Module kind default.** Fast: follow the project (`ESModule` for `@JSImport`). Full: emit a script after Closure. Confirm whether consumers that use `<script type="module">` (Specular's docs client, others) can load that, or whether full must also emit ESM (and then Closure-on-ESM is off the table). Default: leave the linker alone; reshape at splice.
- **One file vs split.** Default one file. `js.dynamicImport` / `ModuleSplitStyle` may want a tiny set on fast. Full stays one file until someone has a measured load-time or cache-busting reason.
- **Source maps.** Fast should preserve or stitch the linker map through rewrite/inline. Full + Closure maps are harder. First version may drop maps on `spliceFull`; that must be explicit, not silent.
- **Closure version.** Pin Scala.js's JAR for maximum familiarity, or a newer GCC now that we are on JDK 25? Default: start on the artifact Scala.js uses; bump only with a size/correctness note. If the linker drops GCC entirely, keep using the compiler JAR as a plugin dependency. Do not switch to a Node minifier to "follow Scala.js".
- **CJS vs ESM vendor files.** Libraries ship both. Prefer ESM for fast-as-modules; for full, either is fine once inlined. Nested specifiers (`foo/plugin`) need their own map entries (each its own pin). Do not invent an npm `"exports"` walk.
- **Resolver search vs explicit source.** Ivy searches `resolvers` in order. Doing that for jsDelivr then unpkg could yield different bytes for the same coordinate. Default: Maven/WebJar if the user asked for a WebJar; otherwise the first *enabled* CDN resolver. Allow pinning a lib to one resolver. Do not silently fall across CDNs.
- **FileCache vs `update`.** WebJars fit `update` in a `Splice` config. CDN files may be easier as `FileCache.file(url + checksum)` than as fake `ModuleID`s. One cache (`csrCacheDirectory`) either way. Confirm the lm-coursier API on sbt 2 before picking; do not use `ModuleID.from` as the advertised API.
- **GitHub release tarballs.** Nice extra resolver. Not required for Phase 2 if jsDelivr/unpkg + WebJars cover typical packages.
- **URL allowlist / corporate mirror.** `spliceResolvers` is the allowlist. A company can add an internal Maven repo of WebJars and drop public CDNs.
- **IR remap vs post-link rewrite.** scalajs-importmap rewrites `@JSImport` in IR to URLs. Default: post-link on emitted JS. IR remap is an optimization later if rewrite is fragile.
- **Linker Closure on as well?** Only if a measured win remains after the post-link pass. Not the default.
- **Escape hatch for un-Closure-able libraries.** `extern` mapping vs fail. Prefer fail until a real library needs it.

## Prior art (none of these is splice)

| Tool | Why it is not splice |
|---|---|
| **[scalajs-bundler](https://scalacenter.github.io/scalajs-bundler/)** | webpack + npm. Two-stage feel (`fastOptJS` / `fullOptJS`) is the UX we copy. No sbt 2. |
| **[scalajs-esbuild](https://github.com/ptrdom/scalajs-esbuild)**, **[sbt-vite](https://github.com/johnhungerford/sbt-vite)**, **[sbt-jsbundler](https://github.com/johnhungerford/sbt-jsbundler)** | Node. |
| **[sbt-scalajs-importmap](https://github.com/armanbilge/scalajs-importmap)** | Rewrites `@JSImport` to CDN URLs. Not sealed. |
| **[sbt-jsdependencies](https://github.com/scala-js/jsdependencies)** | WebJars as global scripts, not ESM `@JSImport`. Officially not recommended for new projects. |
| **Scala.js `fullLinkJS` Closure** | Scala.js graph only. Bare specifiers stay broken. Off by default and deprecated in 1.21. Minify (1.16+) is the Scala-side piece we *do* reuse. |
