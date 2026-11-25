import com.jamesward.zio_git.GitHttp
import zio.*
import zio.http.Client
import zio.test.*
import zio.test.TestAspect.*

// Reads github.com/jamesward/skills, so it needs network access.
object SkillsSourceGitHubSpec extends ZIOSpecDefault:

  override def spec =
    suite("SkillsSource.github")(
      test("reads the skills on main, and they load"):
        for
          files  <- ZIO.serviceWithZIO[SkillsSource](_.files)
          skills <- ZIO.scoped(Skills.load)
        yield assertTrue(
          files.contains("zen-of-james/SKILL.md"),
          skills.entries.exists(_.uri.value == "skill://jamesward/skills/zen-of-james/SKILL.md"),
        )
    ).provide(SkillsSource.github, GitHttp.live, Client.default) @@ withLiveClock @@ timeout(2.minutes)
