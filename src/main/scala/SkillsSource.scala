import com.jamesward.zio_git.*
import zio.*

import java.io.IOException
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

enum SkillsError:
  case Fetch(cause: GitError)
  case Io(cause: IOException)
  case NotServable(skipped: Chunk[com.jamesward.ziohttp.mcp.McpSkillsJarsSkipped])

/** Where the skills come from: every file under a `skills/` directory, keyed by its path in it (`<name>/SKILL.md`). */
trait SkillsSource:
  def files: IO[SkillsError, Map[String, Chunk[Byte]]]

object SkillsSource:

  // The skills on the main branch of github.com/jamesward/skills.
  val github: ZLayer[GitHttp, Nothing, SkillsSource] =
    ZLayer.fromFunction(GitHub(_))

  final case class GitHub(git: GitHttp) extends SkillsSource:
    def files: IO[SkillsError, Map[String, Chunk[Byte]]] =
      val fetch =
        for
          repo   <- ZIO.fromEither(RepoUrl.parse("https://github.com/jamesward/skills.git"))
          path   <- ZIO.fromEither(RepoPath.parse("skills")).orDieWith(IllegalArgumentException(_))
          commit <- git.resolveCommit(repo, Some("main"))
          _      <- ZIO.logInfo(s"Reading skills from ${repo.base} at ${commit.hex}")
          files  <- git.readFilesUnder(repo, commit, path)
        yield files
      fetch.mapError(SkillsError.Fetch(_))

  /** The files under a local directory, for development and tests. */
  final case class Directory(dir: Path) extends SkillsSource:
    def files: IO[SkillsError, Map[String, Chunk[Byte]]] =
      ZIO.attemptBlockingIO:
        val stream = Files.walk(dir)
        try
          stream.iterator().asScala.filter(Files.isRegularFile(_)).map: file =>
            dir.relativize(file).iterator().asScala.mkString("/") -> Chunk.fromArray(Files.readAllBytes(file))
          .toMap
        finally stream.close()
      .mapError(SkillsError.Io(_))
