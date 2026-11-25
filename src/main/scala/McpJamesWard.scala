import com.jamesward.ziohttp.mcp.*
import zio.http.*
import zio.schema.{Schema, derived}
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

object McpJamesWard:

  val name = "mcp.jamesward.com"
  val version = "1.0.0"

  // `GET /health`: heroku-ops' probe. The server only starts once the skills load, so a response means it serves them.
  final case class Health(status: String, skills: Int) derives Schema, CanEqual

  def mcpServer(skills: McpSkillsJars): McpServer[Any] =
    McpServer(name, version)
      .mountedAt("/")
      .withExtensions(skills.extensions)
      .resourceSource(skills.resources)

  def health(skills: McpSkillsJars): Routes[Any, Response] =
    Routes(Method.GET / "health" -> handler(Response(body = Body.from(Health("ok", skills.entries.size)))))

  def routes(skills: McpSkillsJars): Routes[McpServer.State, Response] =
    (health(skills) ++ mcpServer(skills).routes) @@ Middleware.requestLogging() @@ AppLogging.requestId
