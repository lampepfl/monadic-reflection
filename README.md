Monadic Reflection
==================
This project provides support for monadic reflection (Filinski [1994](https://dl.acm.org/citation.cfm?id=178047), [1999](https://doi.org/10.1145/292540.292557))
to integrate monadic code with direct style code.

Dependencies
------------
## Runtime
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
