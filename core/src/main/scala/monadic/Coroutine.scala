package monadic

import java.lang.{ ContinuationScope, Continuation }

private[monadic]
trait Prompt[S, R] {
  def suspend(value: S): R
}

private[monadic] 
class Coroutine[S, R, T](prog: Prompt[S, R] => T) {

  // to disable asserts
  inline def check(inline b: Boolean): Unit = ()

  def isDone = co.isDone
  def value: S = { check(!isDone); receive() }
  def result: T = { check(isDone); receive() }
  def resume(v: R): Unit = { check(!isDone); send(v); co.run() }

  private var channel: Any = null
  private def send(v: Any) = channel = v
  private def receive[A](): A = {
    val v = channel
    v.asInstanceOf[A]
  }

  private object prompt extends ContinuationScope("cats-reflect") with Prompt[S, R] {
    def suspend(value: S): R = {
      send(value)
      Continuation `yield` this
      receive()
    }
  }

  private val co = new Continuation(prompt, () => send(prog(prompt)))

  co.run()
}
