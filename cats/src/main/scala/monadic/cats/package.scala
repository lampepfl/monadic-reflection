package monadic
package cats

import _root_.cats.*

/**
 * If we have a cats monad, we can construct a proper instance of Monadic
 */
class CatsMonadic[M[_]: Monad] extends Monadic[M] {
  def pure[A](a: A) = 
    Monad[M].pure(a)
  def sequence[X, R](init: M[X])(f: X => Either[M[X], M[R]]): M[R] =
    Monad[M].flatten(Monad[M].tailRecM(init) { mx => Monad[M].map(mx)(f) })
}

given [M[_]](using M: Monad[M]): Monadic[M] = CatsMonadic()