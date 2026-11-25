import zio.*
import zio.http.*
import zio.logging.{ConsoleLoggerConfig, LogFilter, LogFormat, consoleJsonLogger, consoleLogger}
import zio.logging.slf4j.bridge.Slf4jBridge

// The SLF4J bridge routes Java library logs (Netty) through ZIO's logger, so they get the same format.
object AppLogging:

  // Production: one JSON object per line, so Heroku's log router keeps a stack trace in one event.
  val json: ZLayer[Any, Nothing, Unit] =
    build(consoleJsonLogger(ConsoleLoggerConfig(jsonFormat, LogFilter.LogLevelByNameConfig(LogLevel.Info))))

  // Development: human-readable lines.
  val text: ZLayer[Any, Nothing, Unit] =
    build(consoleLogger(ConsoleLoggerConfig(textFormat, LogFilter.LogLevelByNameConfig(LogLevel.Debug))))

  // Applied outside `requestLogging` so the request-served line carries the id too.
  val requestId: Middleware[Any] =
    Middleware.logAnnotate((req: Request) =>
      req.headers.get("X-Request-ID").map(LogAnnotation("request_id", _)).toSet
    )

  private def build(console: ZLayer[Any, Nothing, Unit]): ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> console >>> Slf4jBridge.init(LogFilter.logLevel(LogLevel.Info))

  private lazy val textFormat: LogFormat =
    LogFormat.default + LogFormat.space + LogFormat.annotations

  private lazy val jsonFormat: LogFormat =
    import LogFormat.*
    label("timestamp", timestamp) |-|
      label("level", level) |-|
      label("thread", fiberId) |-|
      label("message", quoted(line)) +
      (space + label("cause", cause)).filter(LogFilter.causeNonEmpty) +
      annotations
