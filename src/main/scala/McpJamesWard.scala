import com.jamesward.ziohttp.mcp.*
import zio.http.*

object McpJamesWard:

  val name = "mcp.jamesward.com"
  val version = "1.0.0"

  def mcpServer(skills: McpSkillsJars): McpServer[Any] =
    McpServer(name, version)
      .mountedAt("/")
      .withExtensions(skills.extensions)
      .resourceSource(skills.resources)

  def routes(skills: McpSkillsJars): Routes[McpServer.State, Response] =
    mcpServer(skills).routes @@ Middleware.requestLogging() @@ AppLogging.requestId
