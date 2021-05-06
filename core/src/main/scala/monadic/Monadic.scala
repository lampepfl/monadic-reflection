package monadic

trait Monadic[M[_]] {

  /**
   * Embedding of pure values into the monad M
   */
  def pure[A](a: A): M[A]

  /**
   * Sequencing of monadic values
   *
   * Implementations are required to implement sequencing in a stack-safe
   * way, that is they either need to implement trampolining on their own
   * or implement `sequence` as a tail recursive function.
   *
   * Actually the type X can be different for every call to f...
   * It is a type aligned sequence, but for simplicity we do not enforce this
   * here.
   */
  def sequence[X, R](init: M[X])(f: X => Either[M[X], M[R]]): M[R]

  /**
   * Helper to summon and use an instance of CanReflect[M]
   */
  def reflect[R](mr: M[R])(using r: CanReflect[M]): R = r.reflect(mr)

  /**
   * Reify a computation into a monadic value
   */
  def reify[R](prog: CanReflect[M] ?=> R): M[R] = {

    type X

    // The coroutine keeps sending monadic values until it completes
    // with a monadic value
    val coroutine = new Coroutine[M[X], X, M[R]](prompt => {
      // capability to reflect M
      object reflect extends CanReflect[M] {
        def reflect[R](mr: M[R]) : R =
          // since we know the receiver of this suspend is the
          // call to flatMap, the casts are safe
          prompt.suspend(mr.asInstanceOf[M[X]]).asInstanceOf[R]
      }
      pure(prog(using reflect))
    })

    def step(x: X): Either[M[X], M[R]] = {
      coroutine.resume(x)
      if (coroutine.isDone)
        Right(coroutine.result)
      else
        Left(coroutine.value)
    }

    def run(): M[R] =
      if (coroutine.isDone)
        coroutine.result
      else
        // (M[X] => Either[M[X], M[R]]) => M[R]
        sequence(coroutine.value)(step)

    run()
  }
}

