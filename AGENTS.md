# AGENTS.md

James Ward's MCP server ([jamesward/mcp](https://github.com/jamesward/mcp)) at `https://mcp.jamesward.com/`: a ZIO HTTP server, using [zio-http-mcp](https://github.com/jamesward/zio-http-mcp), built as a GraalVM native binary. For now it serves one feature, Skills. `GET /health` answers 200 with `{"status":"ok","skills":<count>}`; it is heroku-ops' probe.

- **Skills:** at startup, `SkillsSource.github` reads `skills/` on the `main` branch of [jamesward/skills](https://github.com/jamesward/skills) with [zio-git](https://github.com/jamesward/zio-git). `Skills.load` writes the files to a temporary directory, laid out as `META-INF/skills/jamesward/skills/<name>/`, and `McpSkillsJars` serves them as `skill://jamesward/skills/<name>/SKILL.md`. A skill that can't be served (invalid frontmatter, over the size limits) fails startup; finding no skills is a defect. The skills only change when the server restarts (Heroku restarts dynos about every 24 hours).

## Skills

Follow the `zen-of-projects` Skill.

- For Scala 3 and ZIO code (writing or reviewing it), follow `zen-of-scala`.
- For design, domain modeling, effects and testing strategy, follow `zen-of-james`.

Get them with `./sbta extractSkillsJars` (into `.kiro/skills/`), and read the relevant ones before working.

## MCP

The MCP server is `sbta` (`./sbta --mcp`), configured in `.kiro/settings/mcp.json` and `.mcp.json` (enabled in `.claude/settings.json`). In Claude Code its tools are deferred: load them with ToolSearch.

## Build, Test & Dev Workflow

- Use the MCP server `sbta` (this build's `./sbta --mcp`) for all sbt work: build, test and run
  with `compile`, `test` (`tests` for specific classes, `full` for all), `run`, `clean` and
  `publish-local`; inspect the build with `projects`, `test-names`, `main-classes`, `dependencies` and
  `show`. Use `sbt-task` for any other command (e.g. `reload`) and `list-tasks` to find tasks. Don't run
  `sbt` or `./sbta` in a shell while the MCP tools are available; if the server is unavailable, say so and
  fall back to `./sbta <command>`.
- After editing Scala 3 sources, check them with `check` (pass the edited files: a fast type check
  without compiling; `"scope":"module"` after changing an API), then run `compile` and `test` before
  declaring the work done.
- For Scala symbols use `glob-search` (find or list), `inspect` (members and signatures) and
  `symbol-location` (source location) rather than text search or guessing APIs. JavaDoc/ScalaDoc
  lookups are the proxied javadocs.dev tools of the same server.

Full validation (CI runs the same):

```bash
./sbta "Test / compile; testFull; nativeImage"
```

- Dev server: `./sbta dev` (`~Test/runReload`, `AppTest`: text logs) on `http://localhost:8080/`. `PORT` overrides the port. Like production, it reads the skills from GitHub.
- Tests: `McpJamesWardSpec` serves the fixture skills in `src/test/resources/skills/` (and the unservable one in `src/test/resources/broken-skills/`). `SkillsSourceGitHubSpec` reads github.com/jamesward/skills, so it needs network access. No tests need Docker or paid services.
- Native image: `nativeImage` builds the server as a GraalVM native binary at
  `target/out/jvm/scala-3.10.1/mcp-jamesward/native-image/mcp-jamesward` (about 3 minutes, 3 GB of memory).
  sbt-native-image downloads GraalVM Community into `~/.cache/coursier/jvm/`; its version is
  `nativeImageVersion` in `build.sbt` plus the download URLs in `project/graalvm-jvm-index.json`
  (update both together: the plugin's Coursier 2.0.3 can't read the current Coursier JVM index).
  Linking needs `gcc` and zlib (`libz`). A machine whose linker can't find zlib (NixOS) adds
  `-H:NativeLinkerOption=-L<zlib lib dir>` to `nativeImageOptions` in a gitignored `local.sbt`. A library
  that initializes classes at build time can fail the build with "object of type ... was found in the
  image heap"; add the named classes to `--initialize-at-build-time` in `build.sbt` after checking they
  hold no startup state.

## Production

- Heroku app `mcp-jamesward` serving `mcp.jamesward.com`. Buildpacks, in order: `heroku/jvm` (the JDK for sbt during the build), `https://github.com/jamesward/buildpack-scala`, whose native image mode runs `nativeImage` and ships only the binary, at `bin/mcp-jamesward`.
- `Procfile`: `web: bin/mcp-jamesward -XX:+ExitOnOutOfMemoryError -Djava.net.preferIPv4Stack=true`. A native binary ignores `JAVA_TOOL_OPTIONS`, so the JVM defaults buildpack-scala sets for JVM apps are passed as arguments here. The max heap defaults to 80% of the dyno's memory limit.
- Logs JSON, one object per line, with `request_id` from `X-Request-ID`, through zio-logging's `consoleJsonLogger` and the SLF4J bridge (`AppLogging`).
- Runtime metrics (`log-runtime-metrics`): on.
- Watched by heroku-ops (FACTORY.md "Watched Heroku apps"): log drain, webhook and a probe of `/health`.

## Maintenance routine

`.factory/MAINTENANCE.md`.

## Exceptions

- No sbt-native-packager: production runs the GraalVM native binary (sbt-native-image), so there's no `JavaAppPackaging` or `stage`.
