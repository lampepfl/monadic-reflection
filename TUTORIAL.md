# Monadic Reflection for Programmers

> NOTE: This is still work in progress.

Here we give a brief tutorial on the concept of "monadic reflection", targeted towards programmers.

## Should I care?
You might be interested in this concept if...

- you are an **author of an existing library** for functional programming (FP) with effects
- you are an **author of a future library** for FP / fibers / coroutines / effects / ...
- you are generally **inclined to learn** about FP concepts, effects, continuations and more

## Relevant Concepts
There are a few important concepts that we will encounter and explain separately. Feel free to only skim over the corresponding sections, if you are already familiar with them:

- (Side) Effects
- Monads
- Continuation-Passing Style

## (Side) Effects
When reasoning about programs, it often makes sense to distinguish between two kinds of programs: _pure_ and _impure_.

### Pure Programs
We call a program _pure_ if it simply computes and returns a result, without depending on or modifying the state of the world.

The following expressions are examples of pure programs:

```scala
def e1 = 42
def e2 = 1 + 2
def e3 = x => x + 1
def e4 = e3(e1)
```

As you can see also functions like `e3` can be pure expressions. Even calling a function can be pure: all we do here is to compose pure fragements by passing one to another. Sometimes this style of composition is said to be "the essence of (pure) functional programming".

### Impure Programs
We call a program _impure_ or "having side effects" if, besides computing a result it depends on or modifys the state of the world.

The following expressions are examples of impure programs:
```scala
def e5 = println("hello")
def e6 = readLine().toInt
def e7 = throw new Exception("Boom")
def e8 = e3(e6)
```
Printing to (example `e5`) or reading from the console (example `e6`) has effects on the outside world. The same goes for programs that throw exceptions (example `e7`): they do not simply return a value but cause a non-local transfer of the control flow.
Finally, even though the function `e3` is pure, the composed expression `e8` is impure because evaluating the argument will read from the console. Furthermore, maybe confusingly we say that the expression `val e9 = () => e6` **is pure** -- the effects of `e9` are _latent_. Passing around `e9` itself doesn't cause any side effects. However, calling `e8()` reveals the latent effect of reading from the console and is thus impure.

> **State of the World**
> The term "state of the world" is purposefully vague. It can be interpreted as mutable state, files, the current time, databases, network, the current frames on the stack, and many more.

### Values vs. Computation
One important idea of functional programming is to reason about programs as values. To say it in the words of Paul Levy,
a value _is_ while a computation _does_. Values like `42` or `true` simply exist. Mentioning them or passing them around does
not do anything -- in particular it does not cause any (side) effects. In contrast, `e6` is a computation that does something. Mentioning it will trigger the side effect of reading from the console.

```bash
scala> e6 + e6
// typing 1<ENTER> 2<ENTER> into the terminal
res: Int = 3
```

Another common example for side effects are _exceptions_.
The following program will serve us as a running example in the remainder of this tutorial.

```scala
def raise[A](): A = throw new Exception("Division by zero")
def safeDiv(x: Int, y: Int): Int =
  if (y == 0) raise() else return (x / y)

def prog = {
  val res1 = safeDiv(1, 2);
  val res2 = safeDiv(1, 0);
  res1 + res2
}
```

Calling `raise()` is an impure program, and so is `prog`.


## Monads
Historically, the concept of monads has been introduced by researchers to describe the meaning of programs that have side effects. It later has been discovered as a program structuring principle that equips programmers with the power to define their own effects. To the current day, in the Scala language, monads are often used in order to separate the description of a program from actually running it. Importantly, this separation allows programmers to reason about _programs as values_.

There [are](https://medium.com/free-code-camp/demystifying-the-monad-in-scala-cc716bb6f534) [many](https://www.baeldung.com/scala/monads) [introductions](https://blog.redelastic.com/a-guide-to-scala-collections-exploring-monads-in-scala-collections-ef810ef3aec3) to monads out there, so here we just want to give a brief glimpse at the concept and establish terminology.

Informally, a monad is a type `M[_]` with the following methods:
```scala
trait M[A] {
  def flatMap[B](f: A => M[B]): M[B]
}
def pure[A](a: A): M[A]
```
Sometimes those methods have different names (for example `pure` is sometimes called `return`, `unit`, or `point`; and `flatMap` is sometimes called `bind` or `>>=`).

One example of such a type is `Option` where the method `pure` is called `Some`.
A nice (and essential) part about monadic programs is that they can be composed into larger programs.
That is, if we (only) know that `M` is a monad, we can use `flatMap` to compose two programs.
In the following example we concretely use the `Option` monad to express our above running example:

```scala
def pure[A](a: A): Option[A] = Some(a)
def raise[A](): Option[A] = None

def safeDiv(x: Int, y: Int): Option[Int] =
  if (y == 0) raise() else pure(x / y)

def prog =
  safeDiv(1, 2).flatMap { res1 =>
    safeDiv(1, 0).flatMap { res2 =>
      pure(res1 + res2)
    }
  }
```

### For Comprehensions
In fact, in Scala we can use for-comprehensions to write `prog` in a a bit more concise way:

```scala
for {
  res1 <- safeDiv(1, 2)
  res2 <- safeDiv(1, 0)
  res3 <- pure(res1 + res2)
} yield res3
```
Given `flatMap` and `pure`, we can always implement the method
```
def map[B](f: A => B): M[B] = flatMap(a => pure(f(a)))
```
Using map, we can rewrite the example once more to
```scala
safeDiv(1, 2).flatMap { res1 =>
  safeDiv(1, 0).map { res2 => res1 + res2 }
}
```
or using for-comprehensions:
```scala
for {
  res1 <- safeDiv(1, 2)
  res2 <- safeDiv(1, 0)
} yield res1 + res2
```

## Continuation-Passing Style
Another programming language concept, related to effects, is _continuation-passing style_ (or short "CPS").
A program written in CPS has a very special shape: Functions do not return anything, instead they get a function (the _continuation_) as an additional parameter, which they call with the resulting value.

That is, a function like
```scala
def double(x: Int) =
  return x + x
```
is written as:
```scala
def double(x: Int) = k =>
  k(x + x)
```
Here the additional argument `k` is the continuation. We explicitly use `return` above to highlight the connection to the call to `k`.

Calling a function like `double` as in
```scala
def quadruple(x: Int) =
  return double(double(x))
```
is written in CPS as:
```scala
def quadruple(x: Int) = k =>
  double(x) { res1 =>
    double(res1) { res2 =>
      k(res2)
    }
  }
```

### Evaluation Order
Note, how each call to `double` also takes a function argument that binds the result to `resX`.
The CPS version is actually closer to the equivalent direct-style program:
```scala
def quadruple(x: Int) = {
  val res1 = double(x)
  val res2 = double(res1)
  return res2
}
```
This illustrates one historic motivation for CPS: it makes it very clear which function is evaluated exactly in which order. In the direct style version, we can see that we _first_ evaluate `double(x)` and only when this call returns, we _then_ evaluate `double(res1)`.

### Control Effects
However, there is another aspect to programs in CPS, which is much more important for this tutorial: writing a program in CPS gives us direct access to the continuation `k`. It is just a function like any other and we can call it directly, call it later, not call it, call it multiple times, and so on. You might be familiar with this in the context of webprogramming, where
a concept similar to continuations is often referred to as _callbacks_.

To illustrate this additional power, let's look at our running example again.
Here, we first define the type of `CPS` to be:
```scala
type Res
type CPS[A] = (A => Res) => Res
```
In general, a program that returns a value of type `A` in CPS has the type `(A => Res) => Res` for some _answer type_ `Res`.
For simplicity, in our example, we choose `type Res = String`.

We can now express our example as follows
```scala
def raise[A](): CPS[A] = k => "Division by zero"
def safeDiv(x: Int, y: Int): CPS[Int] = k =>
  if (y == 0) raise()(k) else k(x / y)

def prog: CPS[Int] = k =>
  safeDiv(1, 2) { res1 =>
    safeDiv(1, 0) { res2 =>
      k(res1 + res2)
    }
  }
```
and run it with:
```scala
def run[A](p: CPS[A]): Unit =
  println(p(a => a.toString))

run { prog } // prints "Division by zero"
```

## CPS as a Monad
If we compare the running example written in monadic style using `flatMap` and in CPS, we can recognize a similarity.
In fact, in Scala the curried function application `safeDiv(1, 2) { res1 => ... }` is syntactic sugar for the equivalent
`safeDiv(1, 2).apply { res1 => ... }`, showing that the only difference is the name of the function (that is, `flatMap` vs. `apply`). It is thus very easy to turn our `CPS` type into a monad. We simply define an extension method for `flatMap`
and a function `pure`:

```scala
def pure[A](a: A): CPS[A] = k => k(a)

extension [A](prog: CPS[A])
  def flatMap[B](f: A => CPS[B]): CPS[B] = k => prog.apply(a => f(a).apply(k))
  def map[B](f: A => B): CPS[B] = flatMap(a => pure(f(a)))
```

Now again, we can also use for-comprehensions to write `prog` as:

```scala
def prog: CPS[Int] =
  for {
    res1 <- safeDiv(1, 2)
    res2 <- safeDiv(1, 0)
  } yield res1 + res2
```

## Monads as CPS
From the previous example, we can learn that _CPS is a monad_. However, the example also shows that it is not just any monad.
Since the only difference between `flatMap` and `apply` really is the name, the CPS monad can express arbitrary other monads.
It is this power, that has earned it the name ["The Mother of all Monads"](https://www.schoolofhaskell.com/school/to-infinity-and-beyond/pick-of-the-week/the-mother-of-all-monads).

### Option
We can, for instance, very easily re-define:
```scala
type Res = Option[Int]
```
which allows us to express raising exceptions
```scala
def raise[A](): CPS[A] = k => None
def run(prog: CPS[Int]) = prog(a => Some(a))
```

### List
Alternatively, we can choose
```scala
type Res = List[Int]
```
which allows us to embed the List monad and express non-determinism.
```scala
def chooseFrom[A](l: List[A]): CPS[A] = k => l.flatMap(k)
def example: CPS[Int] = for {
  flip1 <- chooseFrom(List(true, false))
  flip2 <- chooseFrom(List(true, false))
  res <- if (flip1 && flip2) 0 else 1
} yield res

def run(prog: CPS[Int]) = prog(a => List(a))
run(example) // results in List(0, 1, 1, 1)
```

These examples illustrate the super powers of the CPS monad. We can compose programs arbitrarily in the CPS monad using
its `flatMap` method. Simply by chosing another type for `Res`, we can suddenly express arbitrary other effects!

## Fibers and CPS
Why should you care as a programmer about all of this? Well...
Monads are a powerful interface to compose effectful programs.
CPS is one particularly powerful monad and allows you to decouple the use of effect operations in effectful programs from the underlying monad.
Programming language design is about handing out just the right abstractions to programmers. If a language has good support for the CPS monad, it automatically also has good support for every other monad.

Importantly, with first-class support of fibers (aka user-level threads) in [Project Loom](https://github.com/openjdk/loom), we take a giant leap towards good support for programming in the CPS monad.
This might be a quite controversial statement and deserves a bit of explanation in the remainder of this tutorial.

### Fibers and Delimited Continuations
To be continued

### Monadic Reflection
To be continued

## The Monadic Reflection Library
To be continued

## Comparison with ...
To be continued

### -Xasync

### Dotty CPS Async
[CPS Async](https://github.com/rssh/dotty-cps-async)

### Monadless
[Monadless](https://github.com/monadless/monadless)
