package cat.stratosphere.isprimed

import cats.effect.Sync
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl


import org.http4s.circe._

import io.circe._, io.circe.generic.auto._

object IsprimedRoutes {
  def helloWorldRoutes[F[_]: Sync](H: HelloWorld[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "hello" / name =>
        for {
          greeting <- H.hello(HelloWorld.Name(name))
          resp <- Ok(greeting)
        } yield resp
    }
  }

  def isPrimeRoutes[F[_]: Sync](ISP: IsPrime[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    //POST /wb/massages.php?nonce=245664686&signature=010744e6046671841ee6cf95b6c18550cbbf209f&timestamp=1570203402125
    object nonceQPM extends QueryParamDecoderMatcher[String]("nonce")
    object signatureQPM extends QueryParamDecoderMatcher[String]("signature")
    object timestampQPM extends QueryParamDecoderMatcher[String]("timestamp")
    object echostrOQPM extends QueryParamDecoderMatcher[String]("echostr")
    implicit val decoder = jsonOf[F, IsPrime.PostMsg]
    HttpRoutes.of[F] {
      case req @ POST -> Root / "isPrime" :? nonceQPM (nonce) 
                                          +& signatureQPM(signature) 
                                          +& timestampQPM(timestamp)
        if ISP.checkSignature (nonce, signature, timestamp) => 
          for {
            pm <- req.as[IsPrime.PostMsg]
            resp <- pm.`type` match {
              case "text" => Ok (ISP.text(pm).getOrElse(Json.Null))
              case "event" => Ok (ISP.event(pm).getOrElse(Json.Null))
            }
          } yield resp
      case GET -> Root / "isPrime"  :? nonceQPM (nonce) 
                                    +& signatureQPM(signature) 
                                    +& timestampQPM(timestamp) 
                                    +& echostrOQPM(echostr) 
        if ISP.checkSignature (nonce, signature, timestamp) => Ok(echostr)
    }
  }
}
