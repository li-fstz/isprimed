package cat.stratosphere.isprimed
import scala.annotation.tailrec

trait PrimeNumber {
    def factorize (x: Long): List[Long] = {
        @tailrec
        def foo (x: Long, a: Long = 2, list: List[Long] = Nil): List[Long] = a * a > x match {
            case false if x % a == 0 => foo (x / a, a, a :: list)
            case false => foo (x, a + 1, list)
            case true => x :: list
        }
        foo(x)
    }
    
    //@tailrec
    def factorize2(x: Long): LazyList[Long] = {
        if (x == 1) LazyList.empty
        else {
            @tailrec
            def foo (i: Long = 3): Long = {
                if (i * i <= x) if (x % i == 0) i else foo(i + 2)
                else x
            }
            val i = if (x % 2 == 0) 2 else foo()
            i #:: factorize2(x / i)
        }
    }
    def format (factors: List[Long]): String = {
        val facList = factors.reverse
        facList.head.toString + facList.tail.map (" × " + _).mkString
    }
    def format2 (factors: List[Long]): String = {
        def zip (factors: List[Long]): List[(Long, Int)] = {
            @tailrec
            def foo (f: List[Long], z: List[(Long, Int)] = Nil): List[(Long, Int)] = f match {
                case Nil => z
                case x::_ =>
                    if (z != Nil && z.head._1 == x) foo (f.tail, (z.head._1, z.head._2 + 1)::z.tail)
                    else foo (f.tail, (x, 1)::z)
            }
            foo (factors)
        }
        //⁰ ¹ ² ³ ⁴ ⁵ ⁶ ⁷ ⁸ ⁹
        def toTop (num: Int): String = {
            val arr = Array ("⁰", "¹", "²" ,"³" ,"⁴" ,"⁵" ,"⁶" ,"⁷" ,"⁸" ,"⁹")
            num.toString.foldLeft("")((s: String, ch: Char) => s + arr(ch.toInt - '0'.toInt))
        }
        val facList = zip (factors).map(i => if (i._2 == 1) i._1.toString else s"""${i._1.toString + toTop(i._2)}""")
        facList.head + facList.tail.map (" × " + _).mkString
    }

    //@tailrec
    def primeTime (time: Long): Option[Long] = {
        def nextTime (t: Long): Option[Long] = {
            if (t % 10000 == 2359) None
            else Some (t + (if (t % 100 == 59) 41 else 1))
        }
        /*
        if (factorize2 (time).head == value) Some(time)
        else nextTime(time) flatMap primeTime
        */
        @tailrec
        def foo (t: Option[Long]): Option[Long] = {
            t match {
                case None => None
                case Some(value) if (factorize2 (value).head == value)=> Some(value)
                case Some(value) => foo(nextTime(value))
            }
        }
        foo(nextTime(time))
    }
}
