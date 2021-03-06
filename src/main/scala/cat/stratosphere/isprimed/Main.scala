package cat.stratosphere.isprimed

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]) =
    IsprimedServer.stream[IO].compile.drain.as(ExitCode.Success)
}
