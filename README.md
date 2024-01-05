Monadic Reflection
==================
_This project provides support for monadic reflection (Filinski [1994](https://dl.acm.org/citation.cfm?id=178047), [1999](https://doi.org/10.1145/292540.292557))
to integrate monadic code with direct style code._

## Tired of writing code using for-comprehensions?
The `monadic-reflection` library provides a convenient alternative!

### Before
```scala
for {
  _ <- monadicActionA()
  r <- monadicActionB()
  result <- if (predicate(r)) {
    monadicActionC()
  } else {
    monadicActionD()
  }
} yield result
```

### After
```scala
effectfulActionA()
if (predicate(effectfulActionB())) {
  effectfulActionC()
} else {
  effectfulActionD()
}
```

Looks familiar? Yes, it is just the direct-style code you would write in an imperative programming language.

## Concepts
The underlying idea is very simple: Instead of using your monadic type constructor `M[A]` everywhere, your effectful programs
now have the type `CanReflect[M] ?=> A` where `CanReflect` is a type defined by the `monadic-reflection` library.

As you can see from the type, given the capability `CanReflect[M]` you immediately get a value of type `A` that you can just use in direct-style. No need for `flatMap` and friends.

The best thing is, that you can go back and forth between the two representations:

```scala
trait Monadic[M[_]] {
  // embed a monadic value into direct style
  def reflect[R](mr: M[R])(using r: CanReflect[M]): R = r.reflect(mr)

  // reveal the monadic structure of a direct-style program
  def reify[R](prog: CanReflect[M] ?=> R): M[R]
}
```

## How can I use this with my monad?
All you need to do is implement the `Monadic` trait which has two abstract methods:

```scala
def pure[A](a: A): M[A]
def sequence[X, R](init: M[X])(f: X => Either[M[X], M[R]]): M[R]
```
The first should look very familiar to you -- and if you already have a monad is very easy to implement. The second is just a slight variation of `flatMap`. In order to be stack safe you need to make sure to either implement `sequence` as a tail recursive function, or perform _trampolining_ on your own.

> Well, there is a fineprint: You also need to run your programs using a special JDK fork called "Project Loom". See below for more details.

## Example Integrations
We provide a few case studies showing how to program with established monadic libraries in direct style:

- [cats](/cats/src/main/scala/monadic/cats)
- [`Future`](/core/src/main/scala/monadic/examples/FutureIO.scala)
- [scalaz](/scalaz/src/main/scala/monadic/examples.scala)
- [zio](/zio/src/main/scala/monadic/examples.scala)

## Dependencies
To implement monadic reflection we require some implementation of
(delimited) continuations. At the moment, our library only runs on JDK >= 21 


### Run Sbt

Finally, since we are accessing jvm internal types (Continuation and ContinuationScope),
we need to allow our program to access them. This is done by forking the process in the `sbt` configuration.

If this does not work for you (for whatever reason), you can try to run `sbt` with:
```
sbt -J--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED
```

Some experimental performance optimizations of project loom can be enabled by
```
-XX:-DetectLocksInCompiledFrames -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseNewCode
```
