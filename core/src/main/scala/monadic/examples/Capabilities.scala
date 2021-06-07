package monadic
package examples
package capabilities

import scala.annotation.implicitNotFound
import scala.concurrent.duration._

/**
 * This example explores the design of a capability-based IO library.
 */
object library {


  /**
   * Abbreviation to describe effectful programs that depend on services and
   * use errors. Most of the time you want to use its friend EFF, though.
   */
  type Eff[-R, +E, +A] = (Service[R], Error[E]) ?=> A


  /**
   * SERVICES
   * --------
   */

  /**
   * The capability to use a service
   *
   * It is covariant in E, so all services are collected in an intersection type (A & B & ...)
   */
  opaque type Service[+E] = E

  /**
   * A service of type E can only be used if its capability is in scope
   */
  def use[E](using service: Service[E]): E = service

  /**
   * The capability for empty services can always be provided
   */
  given Service[Any] = ()

  extension [R, E, A](eff: EFF[R, E, A])
    /**
     * Provide services to the computation, fully satisfying its requirements
     */
    def provide(service: R): EFF[Any, E, A] = eff(using service)


  /**
   * ERRORS
   * ------
   */

  /**
   * The capability to use errors -- no runtime content
   *
   * It is contravariant in E, so all exceptions that can be used are represented in a
   * union type (A | B | ...) or a common supertype.
   */
  opaque type Error[-T] = Unit

  /**
   * An exception can be thrown if a corresponding Error capability is in scope
   */
  def raise[T <: Throwable](e: T)(using Error[T]): Nothing =
    throw ExceptionWrapper(e)

  /**
   * The capability for empty errors can always be provided
   */
  given Error[Nothing] = ()

  /**
   * A runtime wrapper for exceptions to somewhat establish effect safety
   */
  private case class ExceptionWrapper[E](e: E) extends Throwable

  /**
   * Extension methods for error handling
   */
  extension [R, E, A](eff: EFF[R, E, A])

    def catchAll[R1 <: R, E1, A1 >: A](handler: E => Eff[R1, E1, A1]): EFF[R1, E1, A1] =
        try { eff(using use[R1], ()) }
        catch { case ExceptionWrapper(e) => handler(e.asInstanceOf[E]) }

    def catchSome[R1 <: R, E1 >: E, A1 >: A](handler: PartialFunction[E, Eff[R1, E1, A1]]): EFF[R1, E1, A1] =
        try { eff(using use[R1], ()) }
        catch {
          case ExceptionWrapper(e) if handler.isDefinedAt(e.asInstanceOf[E]) =>
            handler(e.asInstanceOf[E])
        }

  /**
   * A helper type to improve effect inference using method `effectful` below.
   *
   * By using this type alias, values of type `EFF[R, E, A]` are **not** automatically coerced into `A`
   * triggering an implicit search.
   *
   * However, since this often is the intended behavior at use site, with the given `autoForce`,
   * we also provide such a coercion.
   */
  opaque type EFF[-R, +E, +A] = (Service[R], Error[E]) ?=> A

  /**
   * Helper method to mark positions where effects should be inferred.
   *
   *   def foo = effectful { use[A].a() + use[B].b() }
   *
   * will infer `EFF[A & B, Nothing, T]` as the type for foo. See also extension method `force`
   * to convert back into `Eff`.
   */
  def effectful[R, E, A](eff: (Service[R], Error[E]) ?=> A): EFF[R, E, A] = eff

  /**
   * Force reveals the underlying representation of EFF and triggers search for
   * service and error capabilities.
   */
  extension [R, E, A](eff: EFF[R, E, A])
    def force(): Eff[R, E, A] = eff

  /**
   * Just an alias for the extension method EFF.force()
   */
  def run[R, E, A](eff: EFF[R, E, A]): Eff[R, E, A] = eff

  /**
   * Since EFF is mostly used at definition sites, at use sites it is most of the time the
   * correct thing to convert back into Eff.
   */
  given autoForce[R, E, A](using service: Service[R], error: Error[E]): Conversion[EFF[R, E, A], A] with
    def apply(eff: EFF[R, E, A]): A = eff

}

import library.*

/**
 * Service interfaces
 */
object services {

  class IOException extends Throwable

  /**
   * Some abstract service declarations
   */
  trait Console {
    def println(line: String): Unit
    def readLine(): String
  }
  object console {
    def println(line: String)(using Service[Console]) =
      use[Console].println(line)

    def readLine()(using Service[Console], Error[IOException]) =
      use[Console].readLine()
  }

  trait Logging {
    def log(msg: String): Unit
  }
  object logging {
    def log(msg: String)(using Service[Logging]) =
      use[Logging].log(msg)
  }

  type IO[+A] = Eff[Futures, IOException, A]

  trait Futures {
    def first[T](f: Service[Futures] ?=> T, g: Service[Futures] ?=> T): T
    def sleep(d: Duration): Unit
  }
  object futures {
    def first[T](f: Service[Futures] ?=> T, g: Service[Futures] ?=> T)(using Service[Futures]): T =
      use[Futures].first(f, g)
    def sleep(d: Duration)(using Service[Futures]): Unit =
      use[Futures].sleep(d)
  }

}

import services.*

/**
 * Service implementations
 */
object implementations {

  object LiveConsole extends Console with Logging {
    def println(line: String) = System.out.println(line)
    def readLine() = scala.io.StdIn.readLine()
    def log(line: String) = System.out.println("LOG: " + line)
  }

  // For illustration purposes, we use the Future monad to provide a future service
  import FutureIO.{ IO as FutureIO }

  import scala.concurrent._
  import ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  import java.util.concurrent.TimeUnit._

  class ScalaFutures(using CanReflect[Future]) extends Futures {
    def first[T](f: Service[Futures] ?=> T, g: Service[Futures] ?=> T): T = {
      val p = Promise[T]
      ScalaFutures.reify { f } foreach { x => p.trySuccess(x) }
      ScalaFutures.reify { g } foreach { x => p.trySuccess(x) }
      FutureIO.reflect { p.future }
    }

    def sleep(d: Duration): Unit =
      FutureIO.reflect { Future { Thread.sleep(d.toMillis) } }
  }
  object ScalaFutures {
    def reify[R](prog: Service[Futures] ?=> R): Future[R] =
      FutureIO.reify {
        effectful { prog }
          .provide { new ScalaFutures }
          .force()
      }
    def run(d: Duration)(prog: IO[Unit]): Unit =
      Await.result(reify {
        effectful { prog }
          .catchAll { case e: IOException => println("boom") }
          .force()
      }, d)
  }
}

/**
 * Example programs
 */
object examples extends App {
  // for autoForce
  import scala.language.implicitConversions
  import implementations.*

  import futures.*

  // Example using Logging and Console
  // ---------------------------------
  class OtherException extends Throwable

  def pure = effectful { 1 + 2 }

  // we can immediately force a pure computation
  pure.force()

  val foo: EFF[Any, Nothing, Int] = effectful { 1 }
  val bar: EFF[Any, Nothing, Int] = effectful { 2 }

  def readPrint: Eff[Console, IOException, Unit] = {
    console.readLine()
    console.println((foo + bar).toString)
  }

  def greeter = effectful {
    val name = console.readLine()
    logging.log(name)
    if (true) { raise(new OtherException()) }
    println("Hello " + name)
  }

  // we cannot force greeter here, since Service[Console & Logging] is missing.
  // greeter.force()

  // here we can force it, of course...
  def main: EFF[Console & Logging, Nothing, Unit] =
    greeter catchAll {
      case e : IOException => println("IO error!")
      case e => println("other error")
    }

  run { main provide LiveConsole }

  // Example using Futures
  // ---------------------
  ScalaFutures.run(5.seconds) {

    val idx: Int = first({
      sleep(1.second)
      println("done 1")
      1
    }, {
      sleep(800.millis)
      println("done 2")
      2
    })

    println(idx)
  }
}