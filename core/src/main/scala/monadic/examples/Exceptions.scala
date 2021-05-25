package monadic
package examples

import scala.annotation.tailrec

/**
 * This example illustrates how exceptions can be implemented by
 * reflecting the Either-monad.
 *
 * We also implement helper
 * - function `raise`, which uses reflect on `Left` to encode raising an exception and
 * - function `handle` which temporarily reifies the computation to match on it
 */
object Exceptions extends App {

  type Exc[A] = Either[Exception, A]

  type Error[A] = CanReflect[Exc] ?=> A

  object Error extends Monadic[Exc] {
    def pure[A](a: A) = Right(a)

    @tailrec
    def sequence[X, R](init: Exc[X])(f: X => Either[Exc[X], Exc[R]]): Exc[R] =
      init match {
        case Left(exc) => Left(exc)
        case Right(x) => f(x) match {
          case Left(mx) => sequence(mx)(f)
          case Right(res) => res
        }
      }

    def raise[R](e: Exception): Error[R] = reflect(Left(e))

    def handle[R](prog: Error[R])(handler: PartialFunction[Exception, R]): Error[R] = {
      reify { prog } match {
         case Left(err) if handler isDefinedAt err => handler(err)
         case m => reflect(m)
      }
    }
  }

  class ExcA extends Exception
  class ExcB extends Exception

  val res = Error.reify {
    Error.handle {
      Error.handle {
        println("before")
        Error.raise(new ExcB)
        println("after")
      }{
        case e: ExcA => println("caught exception A")
      }
    }{
      case e: ExcB => println("caught exception B")
    }
  }

  println(s"Result is ${res}")
}