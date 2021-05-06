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

/**
 * This is a simple example illustrating one potential idiomatic use of
 * our monadic reflection library.
 *
 * It assumes a slight paradigm shift in that we have an implementation monad (here Thunk)
 * that is typically not user visible. In contrast, the type IO[A] now is NOT a monad
 * anymore, but a context function that allows us to use thunked expressions in direct style.
 */
object SimpleIO extends App {

  // the implementation monad
  type Thunk[A] = () => A

  // the user facing type alias (in direct style)
  type IO[A] = CanReflect[Thunk] ?=> A

  // the proof that Thunk is a monad and additional operations on it.
  object IO extends Monadic[Thunk] {
    def pure[A](a: A) = () => a

    // Note that for simplicity this is NOT a tail recursive implementation and will stack overflow!
    def sequence[X, R](init: Thunk[X])(f: X => Either[Thunk[X], Thunk[R]]): Thunk[R] =
      () => f(init()) match {
        case Left(iox) => sequence(iox)(f)()
        case Right(res) => res()
      }

    def delay[R](prog: => R): IO[R] =
      reflect { () => prog }

    def run[R](prog: IO[R]): R = (reify { prog })()
  }

  def hello(): IO[Unit] = IO.delay { println("hello") }

  def helloTwice(): IO[Unit] = { hello(); hello() }

  IO.run {
    helloTwice()
  }
}

/**
 * This is a simple example implementation of IO using Future from the
 * Scala standard library.
 *
 * In the implementation of method IO.first we can see how to selectively
 * reify to compose futures in a non-sequential manner.
 */
object FutureIO extends App {

  import scala.concurrent._
  import ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  import java.util.concurrent.TimeUnit._

  type IO[A] = CanReflect[Future] ?=> A

  object IO extends Monadic[Future] {
    def pure[A](a: A) = Future.successful(a)

    // Note that for simplicity this is NOT a tail recursive implementation and will stack overflow!
    def sequence[X, R](init: Future[X])(f: X => Either[Future[X], Future[R]]): Future[R] =
      init.flatMap(x => f(x) match {
        case Left(y) => sequence(y)(f)
        case Right(res) => res
      })

    def run[R](d: Duration)(prog: IO[R]): R = Await.result(reify { prog }, d)

    /**
      * Runs both computations in parallel and returns the value of the first
      * completion. It does not abort the other computation, though.
      */
    def first[T](f: IO[T], g: IO[T]): IO[T] = {
      val p = Promise[T]
      reify { f } foreach { x => p.trySuccess(x) }
      reify { g } foreach { x => p.trySuccess(x) }
      reflect { p.future }
    }

    def sleep(d: Duration): IO[Unit] = reflect { Future { Thread.sleep(d.toMillis) } }
  }

  IO.run(5.seconds) {

    val idx: Int = IO.first({
      IO.sleep(1.second)
      println("done 1")
      1
    }, {
      IO.sleep(800.millis)
      println("done 2")
      2
    })

    println(idx)
  }
}
