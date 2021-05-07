Monadic Reflection
==================
_This project provides support for monadic reflection (Filinski [1994](https://dl.acm.org/citation.cfm?id=178047), [1999](https://doi.org/10.1145/292540.292557))
to integrate monadic code with direct style code._

## Tired of writing code using for-comprehensions?
The `monadic-reflection` provides a convenient alternative! 

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

- [cats](/lampepfl/monadic-reflection/tree/main/cats/src/main/scala/monadic/cats)
- [zio](/lampepfl/monadic-reflection/tree/main/zio/src/main/scala/monadic)
- [`Future`](/lampepfl/monadic-reflection/blob/main/core/src/main/scala/monadic/examples.scala)

## Dependencies
To implement monadic reflection we require some implementation of
(delimited) continuations. At the moment, our library only runs on
a open JDK fork called [project loom](http://cr.openjdk.java.net/~rpressler/loom/Loom-Proposal.html) with runtime support for coroutines / delimited continuations.

### Download a Loom-enabled JDK
There are early-access builds available at <https://jdk.java.net/loom/>.

### Build Loom

To build the custom JVM yourself, clone the repository
```
git clone https://github.com/openjdk/loom
```

and checkout the continuation branch `cont`:
```
git checkout fibers
```

Detailed instructions on how to build the JDK can be found in the
file `doc/building.md`, in short those are:
```
bash configure
make images
```

### Run Sbt

Finally, run sbt with the newly built JVM. Assuming you checked out
loom into `PATH` and built on a mac, run:
```
sbt -java-home $PATH/build/macosx-x86_64-server-release/images/jdk
```
Obviously the path needs to be adjusted for other operating systems.

Some experimental performance optimizations of project loom can be enabled by
```
-XX:-DetectLocksInCompiledFrames -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseNewCode
```
