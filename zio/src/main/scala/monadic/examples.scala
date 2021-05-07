
package monadic

import zio._
import zio.console._
import java.io.IOException

/**
 * This example illustrates a basic integration of ZIO with monadic reflection.
 *
 * The interface presented here is very preliminary and has a lot of room for
 * ergonomic improvement.
 */
object MyApp extends zio.App {

  type ZIOMonad[R, E] = [A] =>> ZIO[R, E, A]

  class ZIOMonadic[R, E] extends Monadic[ZIOMonad[R, E]] {
    type M[A] = ZIO[R, E, A]
    def pure[A](a: A): ZIO[R, E, A] = ZIO.succeed(a)
    def sequence[X, R](init: M[X])(f: X => Either[M[X], M[R]]): M[R] =
      init.flatMap { x => f(x) match {
        case Left(mx) => sequence(mx)(f)
        case Right(res) => res
      }}
  }

  type Zio[R, E, A] = CanReflect[ZIOMonad[R, E]] ?=> A
  object Zio {
    def reify[R, E, A](p: Zio[R, E, A]): ZIO[R, E, A] = new ZIOMonadic[R, E].reify {
      p
    }
    def reflect[R, E, A](z: ZIO[R, E, A]): Zio[R, E, A] =
      summon[CanReflect[ZIOMonad[R, E]]].reflect(z)
  }

  extension [R, E, A](self: ZIO[R, E, A])
    def perform: Zio[R, E, A] = Zio.reflect(self)

  def run(args: List[String]) =
    Zio.reify { loop(); myAppLogic() }.exitCode

  /**
   * This is the getting started example from:
   *   https://zio.dev/docs/overview/overview_basic_operations
   *
   * translated to direct style.
   */
  def myAppLogic(): Zio[Has[Console.Service], IOException, Unit] =
    putStrLn("Hello! What is your name?").perform
    val name = getStrLn.perform
    putStrLn(s"Hello, ${name}, welcome to ZIO!").perform

  /**
   * Just a loop to test for stack consumption
   */
  def loop(): Zio[Has[Console.Service], IOException, Unit] =
    var i = 100000

    def dec() = ZIO.effectTotal{ i = i - 1 }.perform
    def inc() = ZIO.effectTotal{ i = i + 1 }.perform

    while (i > 0) do
      dec()
      inc()
      dec()
}