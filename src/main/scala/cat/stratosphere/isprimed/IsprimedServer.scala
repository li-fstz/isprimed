package cat.stratosphere.isprimed

import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2.Stream
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import scala.concurrent.ExecutionContext.global
import scala.io.StdIn

object IsprimedServer {
  
  def stream[F[_]: ConcurrentEffect](implicit T: Timer[F]): Stream[F, Nothing] = {
    
    val helloWorldAlg = HelloWorld.impl[F]

    // Combine Service Routes into an HttpApp.
    // Can also be done via a Router if you
    // want to extract a segments not checked
    // in the underlying routes.
    /*
    for {
      appSecret <- IO (StdIn.readLine("input your appSecret: "))
    }
    */
    val appSecret = StdIn.readLine("input your appSecret: ")
    val isPrimeAlg = IsPrime.impl[F](appSecret)
    val httpApp = (
      IsprimedRoutes.helloWorldRoutes[F](helloWorldAlg) <+>
      IsprimedRoutes.isPrimeRoutes[F](isPrimeAlg)
    ).orNotFound

    // With Middlewares in place
    
    val finalHttpApp = Logger.httpApp(true, true)(httpApp)

    for {
      exitCode <- BlazeServerBuilder[F](global)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}
