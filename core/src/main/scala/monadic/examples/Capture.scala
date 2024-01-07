package monadic
package examples
package capture

import language.experimental.captureChecking


object redesign extends App {

  trait Effect {

    /**
     * The monadic type constructor
     */
    type M[A]

    /**
     * The Monad instance for [[ M ]]
     */
    def Monad: Monadic[M]

    /**
     * The interface of effect operations, supported by the monad
     */
    type Operations

    /**
     * Implementation of the effect operations in terms of monadic reflection.
     */
    def Operations: CanReflect[M] ?=> Operations

    /**
     * Runs a [[ program ]] that can make use of the effect [[ Operations ]]
     * to compute a result of type [[ A ]] in monad [[ M ]].
     */
    def apply[A](program: Operations^ ?=> A): M[A] =
      Monad.reify { cap ?=> program(using Operations) }

    /**
     * Makes the effect operations available on the effect instance
     */
    implicit inline def api(self: this.type)(using impl: Operations^): Operations^{impl} = impl
  }


  trait State[S] {
    def get(): S
    def set(s: S): Unit
    def update(fn: S => S): Unit
  }

  class StateMonad[S] extends Effect {

    type M[A] = S => (S, A)

    class Operations(using CanReflect[M]) extends State[S] {
      def get() = Monad.reflect(s => (s, s))
      def set(s: S) = Monad.reflect(_ => (s, ()))
      def update(fn: S => S) = Monad.reflect(s => (fn(s), ()))
    }

    def Operations = new Operations

    class Monad extends Monadic[M] {
      def pure[A](a: A): M[A] = s => (s, a)
      def sequence[X, R](init: M[X])(f: X => Either[M[X], M[R]]): M[R] =
        @scala.annotation.tailrec
        def go(prog: M[X], state: S): (S, R) =
          val (newState, x) = prog(state)
          f(x) match {
            case Left(mx) => go(mx, newState)
            case Right(res) => res(newState)
          }
        s => go(init, s)
    }
    // with capture checking enabled, this cannot be defined by an object
    val Monad = new Monad
  }

  object Number extends StateMonad[Int]

  val result = Number {
    while (Number.get() > 0) {
      Number.set(Number.get() - 1)
      //if (Number.get() == 1) throw new Exception("Abort!")
    }
  }
  try { println(result(1000)) } catch {
    case e: Exception => e.printStackTrace()
  }
}
