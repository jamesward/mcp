import zio.*

// Dev main (`./sbta dev`): the production server with human-readable logs.
object AppTest extends ZIOAppDefault:

  override val bootstrap = AppLogging.text ++ Runtime.enableLoomBasedBlockingExecutor

  def run = App.run
