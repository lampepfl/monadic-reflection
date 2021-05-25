package monadic
package examples

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
