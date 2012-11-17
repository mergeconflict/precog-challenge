precog-challenge
================

This project is an implementation of a disk-backed key-value store, specified
as a "challenge problem" in a [blog post][1] written by John De Goes at Precog.
It's broken down into three chunks:

  - `com.mergeconflict.bplus`: a generic implementation of immutable B+-trees.
  - `com.mergeconflict.precog`: the original API specified in the blog post.
  - `com.mergeconflict.precog.impl`: `bplus` applied to byte arrays with a
    disk storage policy.

Modules in `com.mergeconflict.bplus`
------------------------------------

  - `Id`: a unique identifier for a node, a combination of the upper bound on
    keys contained or referenced by that node, and the distance of the node
    from the tree's leaves.
  - `Identity`: a type class capturing object identity, which I believe is
    needed because Array is horrible.
  - `MutableProvider`: a "policy" trait capturing how nodes are stored and the
    branching factor of the B+-tree.
  - `MutableTree`: a mutable wrapper for `Tree`, mostly satisfying the
    `DiskStore` interface from precog (with the exception of `traverse`, since
    that method expects a `Reader`, which expects byte arrays).
  - `Node`: the meat of the B+-tree algorithm. A `DataNode` contains key-value
    pairs and an `Id` referencing the next `DataNode` in the tree; a `TreeNode`
    contains `Id`s referencing its children.
  - `Put`: the result of a `put` operation, informally a list of which nodes
    were made "dirty" and should be subsequently `flush`ed to storage.
  - `Tree`: a simple wrapper for a root node, the current list of "dirty" nodes
    and the tree's provider.

Modules in `com.mergeconflict.precog`
-------------------------------------

  - `DiskStore`: the interface for a key-value store.
  - `DiskStoreSource`: a factory for `DiskStore`s.
  - `Reader`: an iteratee over key-value byte array pairs.

Modules in `com.mergeconflict.precog.impl`
------------------------------------------

  - `Stream`: a type class to abstract operations on Java `InputStream` and
    `OutputStream` (vaguely similar to Haskell's [`Binary`][2] type class).
  - `TreeDiskStore`: a `MutableTree` specialized to keys and values of type
    `Array[Byte]`, implementing `traverse`.
  - `TreeDiskStoreProvider`: an implementation of `MutableProvider` for
    disk-backed `TreeDiskStore`s.

Notes / TODOs
-------------

  - I hate Arrays: reference identity is for jerks.
  - I hate `for`-comprehensions (compared to Haskell's `do` sugar). I hate them
    so much that I threw out all my code that used `scalaz.Validation` and
    `scalaz.ReaderWriterStateT`. On that note, I also hate "type lambdas" and
    I think I probably hate scalaz.
  - I need to tweak the tests to use much larger data sets.
  - I haven't implemented Precog's consistency requirement ("If the program is
    forcibly terminated at any point during writing to a disk store, then
    retrieving the disk store may not fail and must preserve all information
    prior to the most recent call to `flush`"). Plan is to just implement it by
    copying files around.
  - I haven't properly tested performance.

  [1]: http://precog.com/blog-precog-2/entry/do-you-have-what-it-takes-to-be-a-precog-engineer
  [2]: http://hackage.haskell.org/package/binary