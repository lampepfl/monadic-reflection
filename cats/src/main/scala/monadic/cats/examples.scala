package monadic.cats
package examples

import monadic.syntax.*
import cats.effect.{IO, IOApp}

import java.util.concurrent.Semaphore

object ReaderWriter extends App {

  import cats.data.*
  import cats.implicits.*

  type IntReader[A] = Reader[Int, A]
  type IntWriter[A] = Writer[Int, A]

  def read(): Int in IntReader = Reader[Int, Int] { n => n }.reflect
  def write(n: Int): Unit in IntWriter = Writer.tell(n).reflect

  val res: IntReader[Int] = reify [IntReader] in {
    read() + read() + read()
  }

  println("\nReader Example:")
  println { res.run(2) }

  val res2 = reify [IntWriter] in {
    write(1)
    write(4)
    write(3)
  }

  println("\nWriter Example:")
  println { res2.run(0) }

  def locally[R](n: Int)(prog: => R in IntReader): R =
    reify [IntReader] in { prog } run n

  val resBoth: IntReader[IntWriter[Unit]] = reify [IntReader] in {
    reify [IntWriter] in {
      write(read()) // +1

      Writer(4, ()).reflect

      // locally(read() + 2) { write(read()) } // +3
      write(read()) // +1
    }
  }

  println("\nCombined Example:")
  println { resBoth(1).run(0) }

  // examples from https://typelevel.org/cats-effect/datatypes/io.html
  // translated to use cats-reflect
  import cats.effect.IO
  import cats.effect.unsafe.implicits.global

  def ioa: Unit in IO = IO { println("hey!") }.reflect

  def program: Unit in IO = {
    ioa
    ioa
  }

  println("\nIO Example:")

  (reify [IO] in { program }).unsafeRunSync()

  // cats effect IO trampolining works:
  def fib(n: Int, a: Long = 0, b: Long = 1): Long in IO = {
    val b2 = IO(a + b).reflect

    if (n > 0)
      fib(n - 1, b, b2)
    else
      b2
  }

  def fib2(n: Int, a: Long = 0, b: Long = 1): IO[Long] = reify [IO] in {
    val b2 = IO(a + b).reflect

    if (n > 0)
      fib2(n - 1, b, b2).reflect
    else
      b2
  }

  println("\nFib Example:")
  println {
    (reify [IO] in { fib(6) }).unsafeRunSync()
  }
}

// Rate limiting example from 
//   https://medium.com/disney-streaming/a-rate-limiter-in-15-lines-of-code-with-cats-effect-af09d838857a
object RateLimiter extends IOApp.Simple {
  
  import cats.effect._
  import cats.syntax.all._
  import cats.effect.std.{ Semaphore }

  import scala.concurrent.duration._

  // original rate limiter code
  def rateLimited[A, B](semaphore : Semaphore[IO], function : A => IO[B]): A => IO[B] = input =>
    for {
      _  <- semaphore.acquire
      timerFiber <- IO.sleep(1.second).start
      result <- function(input)
      _ <- timerFiber.join
      _  <- semaphore.release
      } yield result

  // example translated to direct style
  def rateLimitedDirectStyle[A, B](semaphore : Semaphore[IO], function : A => B in IO): A => B in IO = input => {
    semaphore.acquire.reflect;
    val timerFiber = IO.sleep(1.second).start.reflect;
    val result = function(input);
    timerFiber.join.reflect;
    semaphore.release.reflect;
    result
  }

  // "big" dataset
  val myData : List[Int] = (1 to 30).toList

  def process: List[String] in IO = {
    println("Starting to process!")
    val sem = Semaphore[IO](10).reflect
    val limited = rateLimitedDirectStyle(sem, n => { println(s"hey! ${n}"); n.toString })
    // here we need to locally reify, since parTraverse has type:
    //   def parTraverse[A](as: List[A])(f: A => IO[B]): IO[List[B]]
    myData.parTraverse(n => reify[IO] in { limited(n) }).reflect
  }

  override def run: IO[Unit] = reify[IO] in { println(process) }
}