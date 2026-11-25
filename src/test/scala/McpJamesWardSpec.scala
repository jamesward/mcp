import com.jamesward.ziohttp.mcp.*
import com.jamesward.ziohttp.mcp.client.*
import zio.*
import zio.http.*
import zio.test.*
import zio.test.TestAspect.*

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

object McpJamesWardSpec extends ZIOSpecDefault:

  // Tests fork with the project root as the working directory.
  private val skillsDir: Path = Paths.get("src/test/resources/skills")

  private val fixtures: ULayer[SkillsSource] = ZLayer.succeed(SkillsSource.Directory(skillsDir))

  private val skillNames: Task[Set[String]] =
    ZIO.attemptBlocking:
      Files.list(skillsDir).iterator().asScala
        .filter(dir => Files.isRegularFile(dir.resolve("SKILL.md")))
        .map(_.getFileName.toString)
        .toSet

  private def skillUri(name: String): String = s"skill://jamesward/skills/$name/SKILL.md"

  private def connect(port: Int): ZIO[Client & Scope, McpClientError, McpSkillsClient] =
    McpSkillsClient.connect(McpClientConfig(s"http://localhost:$port/"))

  override def spec =
    suite("McpJamesWard")(
      test("serves every skill from the source"):
        for
          names  <- skillNames
          skills <- Skills.load
          port   <- Server.install(McpJamesWard.routes(skills))
          client <- connect(port)
          page   <- client.list()
        yield assertTrue(
          names.nonEmpty,
          page.skills.map(_.uri.value).toSet == names.map(skillUri),
          page.nextCursor.isEmpty,
        )
      ,
      test("a skill's files read back with their content"):
        for
          expected <- ZIO.attemptBlocking(Files.readString(skillsDir.resolve("hello/references/greetings.md")))
          skills   <- Skills.load
          port     <- Server.install(McpJamesWard.routes(skills))
          client   <- connect(port)
          uri      <- ZIO.fromEither(McpSkillUri.parse(skillUri("hello")))
          entry    <- client.get(uri)
          body     <- client.readSkill(uri)
          refUri   <- ZIO.fromEither(McpSkillResourceUri.parse("skill://jamesward/skills/hello/references/greetings.md"))
          refs     <- client.readResource(refUri)
        yield assertTrue(
          entry.uri == uri,
          body.flatMap(_.mimeType) == Chunk("text/markdown"),
          refs.flatMap(_.text) == Chunk(expected),
        )
      ,
      test("a skill that can't be served fails the load"):
        for exit <- Skills.load.provideSome[Scope](ZLayer.succeed(SkillsSource.Directory(Paths.get("src/test/resources/broken-skills")))).exit
        yield assertTrue(exit match
          case Exit.Failure(cause) => cause.failureOption.exists:
              case SkillsError.NotServable(skipped) => skipped.size == 1
              case _                                => false
          case Exit.Success(_) => false
        )
      ,
      test("log entries made while handling a request carry its X-Request-ID"):
        for
          skills <- Skills.load
          routes  = McpJamesWard.routes(skills)
          _      <- routes.runZIO(Request.post(URL.root, Body.empty).addHeader("X-Request-ID", "abc-123"))
          _      <- routes.runZIO(Request.post(URL.root, Body.empty))
          output <- ZTestLogger.logOutput
        yield
          val ids = output.map(_.annotations.get("request_id"))
          assertTrue(
            ids.contains(Some("abc-123")),
            ids.contains(None),
            ids.flatten.forall(_ == "abc-123"),
          )
      ,
    ).provideSome[Scope](
      Server.defaultWith(_.onAnyOpenPort),
      Client.default,
      McpServer.State.default,
      fixtures,
    ) @@ withLiveClock @@ timeout(2.minutes) @@ sequential
