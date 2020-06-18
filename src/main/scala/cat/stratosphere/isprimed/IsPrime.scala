package cat.stratosphere.isprimed

import io.circe._, io.circe.generic.auto._, io.circe.syntax._
import cats.effect.Sync
import java.util.Calendar
trait IsPrime[F[_]] {
    def text (pm: IsPrime.PostMsg): Option[Json]
    def event (pm: IsPrime.PostMsg): Option[Json]
    def checkSignature (nonce: String, signature: String, timestamp: String): Boolean
}
object IsPrime extends PrimeNumber {
    case class PostMsg (
        `type`: String,
        text: String,
        receiver_id: Long,
        sender_id: Long,
        data: Data
    )
    case class Data (
        subtype: Option[String] = None,
        key: Option[String] = None
    )
    case class ReplyMsg (
        sender_id: Long,
        receiver_id: Long,
        data: String,
        `type`:String = "text"
    )
    case class Text (text: String)
    object DataUnapply {
        def unapply (str: String): Option[String] = {
            val date = """(\d{1,2}) *(月|\.) *(\d{1,2})""".r
            date.findFirstMatchIn(str) map (value => replyString(value.group(1).toInt, value.group(3).toInt))
        }
        def replyString (month: Int, day: Int) = {
            val days = Array (0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            if (month < 1 || month > 12 || day < 1 || day > days(month)) {
                "ERROR: Illegality Birthday."
            } else if (day % 2 == 0 || day % 5 == 0) {
                "你的下一个质数生日不存在"
            } else {
                val c = Calendar.getInstance 
                val dy = (if (month < (c.get(Calendar.MONTH) + 1) || ((c.get(Calendar.MONTH) + 1) == month && day < c.get(Calendar.DATE))) 1 else 0) to 10000
                val bd = c.get(Calendar.YEAR) * 10000 + month * 100 + day
                val pbd = dy.find {
                    y => factorize (bd + y * 10000).length == 1 && ((month != 2 || day != 29) || (c.get(Calendar.YEAR) + y) % 4 == 0)
                }.get
                s"你的下一个质数生日是\n${pbd + c.get(1)} 年 $month 月 $day 日"
            }
        }
    }
    object NumberUnapply {
        def unapply (str: String): Option[String] = {
            val number = """(\d+)""".r
            number findFirstIn(str) map replyString
        }
        def replyString (n: String) = {
            if (n.length <= 16) {
                val num = n.toLong
                if (num > 1 && num.toDouble <= 1e15) {
                    val factors = factorize(num)
                    if (factors.length == 1) {
                        s"$num 是一个质数"
                    } else {
                        s"$num 不是一个质数\n$num = ${format2 (factors)}"
                    }
                } else {
                    "ERROR: Num Limit Exceeded.\nNOTICE: 2 ≤ x ≤ 1e15."
                }
            } else {
                "ERROR: Num Limit Exceeded.\nNOTICE: 2 ≤ x ≤ 1e15."
            }
        }
    }
    object NextPrimeTimeUnapply {
        def unapply (str: String): Option[String] = {
            if (str.indexOf("下一个质数时间") != -1 || str == "nextPrimeTime") 
                Some(replayString(Calendar.getInstance()))
            else
                None
        }
        def replayString (c: Calendar) = {
            val time = (c.get(Calendar.YEAR) * 10000L + (c.get(Calendar.MARCH) + 1) * 100 + c.get(Calendar.DATE)) * 10000 + c.get(Calendar.HOUR_OF_DAY) * 100 + c.get(Calendar.MINUTE)
            primeTime(time) match {
                case None => "今天已经没有质数时间了"
                //2020.06.05 20:51
                case Some (value) => {
                    val y = value / 100000000
                    val m = value % 100000000 / 1000000
                    val d = value % 1000000 / 10000
                    val h = value % 10000 / 100
                    val mm = value % 100
                    "下一个质数时间为 " + String.format("%d.%02d.%02d %02d:%02d", y, m, d, h, mm)
                }
            }
        }
    }
    def impl[F[_]: Sync](appSecret: String): IsPrime[F] = new IsPrime[F] {
        def checkSignature (nonce: String, signature: String, timestamp: String): Boolean = {
            val arr = Array (nonce, appSecret, timestamp)
            val md = java.security.MessageDigest.getInstance("SHA-1") 
            md.digest(arr.sorted.mkString.getBytes).map("%02x".format(_)).mkString == signature
        }
        def makeReply (pm: PostMsg) (str: String): Some[Json] = {
            import java.net.URLEncoder
            Some(ReplyMsg(pm.receiver_id, pm.sender_id, URLEncoder.encode(Text(str).asJson.noSpaces, "UTF8")).asJson)
        }
        def text (pm: PostMsg): Option[Json] = {
            def ret = makeReply (pm) (_)
            pm.text match {
                case DataUnapply (s) => ret(s)
                case NumberUnapply (s) => ret(s)
                case NextPrimeTimeUnapply (s) => ret(s)
                case str if str.indexOf("生日") != -1 => 
                    ret (
                        """|是想问你的下一个质数生日吗？
                           |你可以发给我你的生日，
                           |如：1 月 1 日""".stripMargin
                    )
                case "li-fstz" => ret ("Yes!")
                case "version" => ret ("isprimed version 1.2.0")
                case _ => None
            }
        }
        def event (pm: PostMsg): Option[Json] = {
            def ret = makeReply (pm) (_)
            pm.data.subtype.get match {
                case "follow" => ret (
                    """|这里是一个质数 bot 的微博。
                       |你可以发给我一个数 x
                       |(2 ≤ x ≤ 10^15)，
                       |我会帮你判断 x 是不是一个质数；
                       |你也可以发给我你的生日，
                       |如：1 月 1 日，
                       |我会告诉你你的下一个质数生日；
                       |你也可以发给我“下一个质数时间”，
                       |我会告诉你今天的下一个质数时间。
                       |
                       |P.S. 如果没有收到回复的话可以尝试再次发送或者给我留言""".stripMargin
                )
                case "click" => pm.data.key.get match {
                    case "today" => {
                        val c = Calendar.getInstance 
                        val today = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MARCH) + 1) * 100 + c.get(Calendar.DATE)
                        val factors = factorize(today)
                        if (factors.length == 1) {
                            ret (s"今天是一个质数，\n$today 是一个质数")
                        } else {
                            ret (s"今天不是一个质数，\n$today = ${format2 (factors)}")
                        }
                    }
                    case "isPrime" => ret (
                        """|你可以发给我一个数 x
                           |(2 ≤ x ≤ 10^15)，
                           |我会帮你判断 x 是不是一个质数""".stripMargin
                    )
                    case "birthday" => ret (
                        """|你可以发给我你的生日，
                           |如：1 月 1 日，
                           |我会告诉你你的下一个质数生日""".stripMargin
                    )
                    case NextPrimeTimeUnapply (s) => ret(s)
                }
            }
        }    
    }
}
