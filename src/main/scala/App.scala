import com.jamesward.ziohttp.mcp.{McpServer, McpSkillsJars}
import com.jamesward.zio_git.GitHttp
import zio.*
import zio.http.*

object App extends ZIOAppDefault:

  override val bootstrap = AppLogging.json ++ Runtime.enableLoomBasedBlockingExecutor

  // Heroku sets PORT. Without it (local runs) zio-http's default port 8080 is used.
  val serverLayer: ZLayer[Any, Throwable, Server] =
    ZLayer.fromZIO(System.env("PORT")).flatMap: env =>
      env.get match
        case None => Server.default
        case Some(value) =>
          value.toIntOption match
            case Some(port) => Server.defaultWithPort(port)
            case None       => ZLayer.fail(IllegalArgumentException(s"PORT is not a number: $value"))

  def run =
    ZIO.serviceWithZIO[McpSkillsJars](skills => Server.serve(McpJamesWard.routes(skills)))
      .provide(serverLayer, McpServer.State.default, Skills.layer, SkillsSource.github, GitHttp.live, Client.default)
