import com.jamesward.ziohttp.mcp.*
import zio.*

import java.nio.file.{Files, Path}
import java.util.Comparator

object Skills:

  // Served as skill://jamesward/skills/<name>/SKILL.md, the SkillsJars layout for github.com/jamesward/skills.
  private val Namespace = "jamesward/skills"

  /**
   * Writes the source's skills to a temporary directory, removed when the scope closes, and loads them.
   * Fails when a skill can't be served (bad frontmatter or over the size limits), so a broken skill
   * fails startup and the tests instead of silently disappearing. No skills at all is a defect.
   */
  val load: ZIO[SkillsSource & Scope, SkillsError, McpSkillsJars] =
    for
      files <- ZIO.serviceWithZIO[SkillsSource](_.files)
      dir   <- tempDir
      _     <- write(dir.resolve(McpSkillsJars.ResourcePrefix).resolve(Namespace), files)
      jars  <- McpSkillsJars.load(Chunk(dir)).refineToOrDie[java.io.IOException].mapError(SkillsError.Io(_))
      _     <- ZIO.fail(SkillsError.NotServable(jars.skipped)).when(jars.skipped.nonEmpty)
      _     <- ZIO.dieMessage(s"No skills found in ${files.size} files").when(jars.entries.isEmpty)
    yield jars

  val layer: ZLayer[SkillsSource, SkillsError, McpSkillsJars] = ZLayer.scoped(load)

  private val tempDir: ZIO[Scope, SkillsError, Path] =
    ZIO.acquireRelease(ZIO.attemptBlockingIO(Files.createTempDirectory("skills")).mapError(SkillsError.Io(_))): dir =>
      ZIO.attemptBlockingIO:
        val stream = Files.walk(dir)
        try stream.sorted(Comparator.reverseOrder()).forEach(Files.delete)
        finally stream.close()
      .ignoreLogged

  private def write(root: Path, files: Map[String, Chunk[Byte]]): IO[SkillsError, Unit] =
    ZIO.foreachDiscard(files): (name, bytes) =>
      val file = root.resolve(name).normalize()
      if !file.startsWith(root) then ZIO.dieMessage(s"Skill file outside the skills directory: $name")
      else
        ZIO.attemptBlockingIO:
          Files.createDirectories(file.getParent)
          Files.write(file, bytes.toArray)
        .unit.mapError(SkillsError.Io(_))
