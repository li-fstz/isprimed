package cat.stratosphere.isprimed

import scala.annotation.tailrec
import io.circe.{Encoder, Decoder, Json, HCursor}
import io.circe.syntax._
import cats.effect.Sync
import io.circe.literal._
trait IsPrime[F[_]]{
    def text (pm: IsPrime.PostMsg): Option[Json]
    def event (pm: IsPrime.PostMsg): Option[Json]
    def checkSignature (nonce: String, signature: String, timestamp: String): Boolean
}
object IsPrime {
    case class Data (
        subtype: Option[String] = None,
        key: Option[String] = None
    )
    case class PostMsg (
        `type`: String,
        text: String,
        receiver_id: Long,
        sender_id: Long,
        data: Data
    )
    case class ReplyMsg (
        sender_id: Long,
        receiver_id: Long,
        text: String
    )
    def impl[F[_]: Sync](appSecret: String): IsPrime[F] = new IsPrime[F]{
        def checkSignature (nonce: String, signature: String, timestamp: String): Boolean = {
            val arr = Array (nonce, appSecret, timestamp)
            val md = java.security.MessageDigest.getInstance("SHA-1") 
            md.digest(arr.sorted.mkString.getBytes).map("%02x".format(_)).mkString == signature
        }
        implicit val MsgEncoder: Encoder[ReplyMsg] = {
            def textData (text: String) = java.net.URLEncoder.encode(json"""{"text":$text}""".toString, "UTF8")
            Encoder.instance { 
                rm: ReplyMsg =>
                json"""{"sender_id":${rm.receiver_id.toString},
                        "receiver_id":${rm.sender_id.toString},
                        "type":"text",
                        "data":${textData(rm.text)}}"""
            }
        }
        def factorize (x: BigInt): List[BigInt] = {
            @tailrec
            def foo (x: BigInt, a: BigInt = 2, list: List[BigInt] = Nil): List[BigInt] = a * a > x match {
                case false if x % a == 0 => foo (x / a, a, a :: list)
                case false => foo (x, a + 1, list)
                case true => x :: list
            }
            foo(x)
        }
        def makeReply (pm: PostMsg) (str: String): Some[Json] = Some(ReplyMsg(pm.sender_id, pm.receiver_id, str).asJson)
        def text (pm: PostMsg): Option[Json] = {
            val date = """(\d{1,2}) *月 *(\d{1,2}) *日""".r
            val number = """^(\d+)$""".r
            def ret = makeReply (pm) (_)
            pm.text match {
                case date (m, d) => {
                    val month = m.toInt
                    val day = d.toInt
                    val days = Array (0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
                    if (month < 1 || month > 12 || day < 1 || day > days(month)) {
                        ret ("ERROR: Illegality Birthday.")
                    } else if (day % 2 == 0 || day % 5 == 0) {
                        ret ("你的下一个质数生日不存在")
                    } else {
                        val c = java.util.Calendar.getInstance 
                        val dy = (if (month < (c.get(2) + 1) || ((c.get(2) + 1) == month && day < c.get(5))) 1 else 0) to 10000
                        val bd = c.get(1) * 10000 + month * 100 + day
                        val pbd = dy.find {
                            y => 
                            factorize (bd + y * 10000).length == 1 && ((month != 2 || day != 29) || (c.get(1) + y) % 4 == 0)
                        }.get
                        ret (s"你的下一个质数生日是\n${pbd + c.get(1)} 年 $month 月 $day 日")
                    }
                }
                case number (n) => {
                    if (n.length <= 16) {
                        val num = BigInt(n)
                        if (num > 1 && num.toDouble <= 1e15) {
                            val facList = factorize(num).reverse
                            if (facList.length == 1) {
                                ret (s"$num 是一个质数")
                            } else {
                                ret (s"$num 不是一个质数\n${num.toString + " = " + facList.head + facList.tail.map (" × " + _).mkString}")
                            }
                        } else {
                            ret ("ERROR: Num Limit Exceeded.\nNOTICE: 2 ≤ x ≤ 1e15.")
                        }
                    } else {
                        ret ("ERROR: Num Limit Exceeded.\nNOTICE: 2 ≤ x ≤ 1e15.")
                    }
                }
                case "li-fstz" => None
                case _ => None
            }
        }
        def event (pm: PostMsg): Option[Json] = {
            def ret = makeReply (pm) (_)
            pm.data.subtype.get match {
                case "follow" => ret ("这里是一个质数 bot 的微博，\n你可以发给我一个数 x\n(2 ≤ x ≤ 10^15)，\n我会帮你判断 x 是不是一个质数\n你也可以发给我你的生日，\n如：1 月 1 日，\n我会告诉你你的下一个质数生日\nP.S. 如果没有收到回复的话可以尝试再次发送或者给我留言")
                case "click" => pm.data.key.get match {
                    case "today" => {
                        val c = java.util.Calendar.getInstance 
                        val today = c.get(1) * 10000 + (c.get(2) + 1) * 100 + c.get(5)
                        val facList = factorize(today).reverse
                        if (facList.length == 1) {
                            ret (s"今天是一个质数，\n$today 是一个质数")
                        } else {
                            ret (s"今天不是一个质数\n${today.toString + " = " + facList.head + facList.tail.map (" × " + _).mkString}")
                        }
                    }
                    case "isPrime" => ret ("你可以发给我一个数 x\n(2 ≤ x ≤ 10^15)，\n我会帮你判断 x 是不是一个质数")
                    case "birthday" => ret ("你可以发给我你的生日，\n如：1 月 1 日，\n我会告诉你你的下一个质数生日")
                }
            }
        }    
    }
}