package monadic
package examples

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

