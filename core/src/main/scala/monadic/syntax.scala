package monadic
package syntax

  type in[A, M[_]] = CanReflect[M] ?=> A

  inline def reify[M[_]: Monadic]: ReifyBuilder[M] = ReifyBuilder()

  case class ReifyBuilder[M[_]]()(using M: Monadic[M]) {
    inline def in[R](prog: R in M) = M.reify[R] { prog }
    inline def apply[R](prog: R in M) = M.reify[R] { prog }
  }

  extension [M[_], R](mr: M[R])
    inline def reflect(using r: CanReflect[M]): R = r.reflect(mr)