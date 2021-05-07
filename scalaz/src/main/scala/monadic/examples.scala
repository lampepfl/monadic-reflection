package monadic

import scalaz.*
import scalaz.Scalaz.*
import scalaz.effect.*

import monadic.syntax.*

/**
 * This example illustrates a basic integration of ZIO with monadic reflection.
 *
 * The interface presented here is very preliminary and has a lot of room for
 * ergonomic improvement.
 */
object MyApp {

  class ScalazMonadic[M[_]: Monad : BindRec] extends Monadic[M] {
    def pure[A](a: A): M[A] = a.pure

    def sequence[X, R](init: M[X])(f: X => Either[M[X], M[R]]): M[R] =
      Monad[M].join(BindRec[M].tailrecM(init) { mx =>
        Monad[M].map(mx) { x => \/.fromEither(f(x)) }
      })
  }

  given [M[_]](using Monad[M], BindRec[M]): Monadic[M] = ScalazMonadic()

  def main(): IO[_] = {
    reify [IO] in {
      IO.putStr("Hello, ").reflect
      IO.putStrLn("world!").reflect
    }
  }

  def main(args: Array[String]): Unit =
    main().unsafePerformIO
}
