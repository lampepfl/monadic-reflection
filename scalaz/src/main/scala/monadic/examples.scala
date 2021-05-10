package monadic

import scalaz.*
import Scalaz.*
import effect.*

import monadic.syntax.*

class ScalazMonadic[M[_]: Monad : BindRec] extends Monadic[M] {
  def pure[A](a: A): M[A] = a.pure

  def sequence[X, R](init: M[X])(f: X => Either[M[X], M[R]]): M[R] =
    Monad[M].join(BindRec[M].tailrecM(init) { mx =>
      Monad[M].map(mx) { x => \/.fromEither(f(x)) }
    })
}

given [M[_]](using Monad[M], BindRec[M]): Monadic[M] = ScalazMonadic()

/**
 * This example illustrates a basic integration of ZIO with monadic reflection.
 *
 * The interface presented here is very preliminary and has a lot of room for
 * ergonomic improvement.
 */
object MyApp {

  type IntReader[A] = Reader[Int, A]
  type IntWriter[A] = Writer[Int, A]

  def read(): Int in IntReader = Reader[Int, Int] { n => n }.reflect
  def write(n: Int): Unit in IntWriter = n.tell.reflect

  def main(args: Array[String]): Unit = {
    def io = reify [IO] in {
      IO.putStr("Hello, ").reflect
      IO.putStrLn("world!").reflect
    }

    io.unsafePerformIO

    val res: IntReader[Int] = reify [IntReader] in {
      read() + read() + read()
    }

    println("\nReader Example:")
    println { res.run(2) }

    val res2 = reify [IntWriter] in {
      write(1)
      write(4)
      write(3)
    }

    println("\nWriter Example:")
      println { res2.run(0) }

    val resBoth: IntReader[IntWriter[Unit]] = reify [IntReader] in {
      reify [IntWriter] in {
        write(read()) // +1

        4.tell.reflect

        write(read()) // +1
      }
    }

    println("\nCombined Example:")
    println { resBoth(1).run(0) }

    def loop(): Unit = reify[IO] in {
      var i = 100000

      def dec() = IO { i = i - 1 }.reflect
      def inc() = IO { i = i + 1 }.reflect

      while (i > 0) do {
        dec()
        inc()
        dec()
      }
    }

    loop()
  }

}
