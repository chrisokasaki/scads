# API Design for Heaps (aka Priority Queues)

A heap (or priority queue) is a collection of elements ordered by some `Ordering`, optimized for retrieving the first element according to that ordering. Duplicate elements are allowed. Applications vary in whether they need the first element to be the smallest or the biggest element according to the ordering, so both variations should be easy to use.  (However, any given heap is expected to offer easy access to either the smallest element only or the biggest element only, not both at the same time.)  I will consider immutable heaps in this document, but the core issues discussed below apply to both immutable heaps and mutable heaps.

Even if an element type has a natural ordering, that ordering may not be the one we want to use, so we must allow the user to specify the ordering.

## Problem 1: Don't mix two different orderings in the same heap

Here is a strawman design for a very simple heap API:
```scala
trait Heap[Elem] {  // WARNING: THIS IS BROKEN!!!
  def isEmpty(implicit ord: Ordering[Elem]): Boolean
  def add(elem: Elem)(implicit ord: Ordering[Elem]): Heap[Elem]
  def first(implicit ord: Ordering[Elem]): Elem
  def rest(implicit ord: Ordering[Elem]): Heap[Elem]
}

// factory method, probably in some companion object
def empty[Elem](implicit ord: Ordering[Elem]): Heap[Elem]
```
You may think it strange that all of these methods are taking an `ord` parameter. From a Scala point of view, that doesn't make much sense.  But you can find variations of this design in many implementations of heaps on GitHub, including in the well-respected Scalaz library. Why? As far as I can tell, the answer is __because Haskell does it that way__.  Here's the equivalent design in Haskell:
```haskell
type Heap a
empty   :: Ord a => Heap a
isEmpty :: Ord a => Heap a -> Bool
add     :: Ord a => a -> Heap a -> Heap a
first   :: Ord a => Heap a -> a
rest    :: Ord a => Heap a -> Heap a
```
In Haskell, this makes perfect sense.  Behind the scenes, each method takes
an Ord dictionary as a hidden parameter. But there's one critical difference between Haskell and Scala: in Haskell, there can only be a _single_ ordering for an element type, but in Scala, there can be _many_ orderings for the same element type.

For example, consider this Scala code
```scala
val ord1 = Ordering.Int
val ord2 = ord1.reverse
val heap1 = empty[Int](ord1).add(5)(ord1).add(7)(ord1)
val heap2 = heap1.add(4)(ord2)
println(heap2.first)
```
What should this print? Of course, that depends on the details of the implementation, but you would expect it to print either 4 (the smallest element) or 7 (the biggest element).  However, because one ordering was used for two of the `add`s and the opposite ordering was used for the third `add`, there's an excellent chance that the actual result will be 5, which is the wrong answer for both orderings.

The magic of implicit parameters is that you usually don't need to pass them explicitly. But (A) there's nothing to stop you from doing so, and (B) there's nothing to prevent you from calling methods in different scopes with different orderings. No, if you're anything like me, the possibility that this could happen by accident is making your skin crawl.  Surely, the API should prevent this from happening!

Fortunately, this problem is very easy to fix.  __Only the `empty` method should take an ordering.__  Once that initial heap has been created, all future heaps derived from that heap via any sequence of `add`s or `rest`s should use the same ordering.  With this change, the API becomes
```scala
trait Heap[Elem] {
  def isEmpty: Boolean
  def add(elem: Elem): Heap[Elem]
  def first: Elem
  def rest: Heap[Elem]
}

// factory method, probably in some companion object
def empty[Elem](implicit ord: Ordering[Elem]): Heap[Elem]
```
Yay! That is both simpler and safer.

## Problem 2: `merge`

Another operation supported by many kinds of heaps is `merge`, which combines two heaps into a single heap.  Examples of heaps supporting merge include leftist heaps, skew heaps, binomial heaps (aka binomial queues), Fibonacci heaps, etc.

We can easily add `merge` to the existing `Heap[Elem]` trait.
```scala
trait Heap[Elem] {
  ...
  def merge(other: Heap[Elem]): Heap[Elem]
}
```
However, there are at last two problems with this.  First, traits allow for subclassing, so we might have several different implementations, such as leftist heaps and binomial heaps.  But we only want to merge leftist heaps with leftist heaps and binomial heaps with binomial heaps&mdash;we do NOT want to merge leftist heaps with binomial heaps.

There are several ways to address this problem. For example, leftist heaps and binomial heaps could just use completely separate class/trait hierarchies, and each could use a `sealed trait` to prevent this unwanted mixing of types.

But the code duplication this would entail is unsatisfying.  It would also make it more difficult to share code (such as a testing harness) between different implementations.

Alternatively, we can control the types more precisely by adding a second type parameter for the specific representation being used, as in
```scala
trait MHeap[Elem, Heap] {
  // MHeap is "Mergeable Heap"
  // Heap is the specific Heap representation being used
  def isEmpty: Boolean
  def add(elem: Elem): Heap
  def first: Elem
  def rest: Heap
  def merge(other: Heap): Heap
}

sealed trait LeftistHeap[Elem] extends MHeap[Elem, LeftistHeap[Elem]]

sealed trait BinomialHeap[Elem] extends MHeap[Elem, BinomialHeap[Elem]]
```
Because of the extra type parameter, a leftist heap and binomial heap are incompatible and cannot be merged.  

## Problem 3: `merge` (continued)

There's a second problem with `merge`.  A particular implementation, such as leftist heaps, would provide a factory method for creating a new heap.
```scala
object LeftistHeap { // companion object
  def empty[Elem](implicit ord: Ordering[Elem]): LeftistHeap[Elem] = ...
}
```
Because of the `MHeap` definition, we can't `merge` a leftist heap with a binomial heap.  But now we've re-introduced the problem of incompatible orderings!
```scala
val ord1 = Ordering.Int
val ord2 = ord1.reverse
val heap1 = empty[Int](ord1).add(5).add(7)
val heap2 = empty[Int](ord2).add(6).add(4)
var heap3 = heap1.merge(heap2)
while (!heap3.isEmpty) {
  println(heap3.first)
  heap3 = heap3.rest
}
```
Notice that `heap1` and `heap2` were created with opposite orderings. What happens if we merge them? Nothing good! The exact results depend on details of the implementation, but a likely result is that loop will print the elements in the order 5,6,4,7&mdash;or maybe 5,7,6,4&mdash;when it _should_ print them in sorted order!

We would really like to make this sort of situation impossible! Maybe we could test the orderings for object equality at runtime, and throw an exception if they're different?  That could actually work for simple types like integers with a built-in ordering object.  But for more complicated types, such as tuples, the orderings are generated on demand from the orderings of their constituent parts. And this generation is not memoized, so if we demand an ordering for, say, `(Int,String)` twice, we'll get two separate ordering objects, which will cause a false negative for our hypothetical dynamic equality check.

No, we would really like to make merging two heaps with different orderings a type error.  We can achieve this by making the notion of a factory explicit.  The idea is that heaps can only be merged with other heaps from the same factory.  Attempting to merge heaps from different factories will cause a type error.

In code, we might express this as follows:
```scala
trait HeapFactory {
  type Elem
  type Heap <: MHeap[Elem,Heap]

  def empty: Heap
  // plus other factory methods
}

object LeftistHeap { // companion object
  def factory[E](implicit ord: Ordering[E]): HeapFactory { type Elem = E } = ...
}
```
Now we can say
```scala
val minHeaps = LeftistHeap.factory[Int](Ordering.Int)
val maxHeaps = LeftistHeap.factory[Int](Ordering.Int.reverse)

val heap1 = minHeaps.empty.add(5).add(7)
val heap2 = minHeaps.empty.add(6).add(4)
val heap3 = heap1.merge(heap2) // this typechecks

val heap4 = maxHeaps.empty.add(6).add(4)
val heap5 = heap1.merge(heap4) // !!!type error!!!
```
Notice that `heap1`, `heap2`, and `heap3` have type `minHeaps.Heap` but `heap4` has type `maxHeaps.Heap`.  According to Scala's notion of __path-dependent types__, these types are incompatible so attempting to merge `heap1` and `heap4` causes a type error, as desired.

## A question

Clearly, if I create two factories with incompatible element types, then a heap from one factory should not be mergeable with a heap from the other factory.  Similarly, if I create two factories with the same element type but incompatible orderings, then again a heap from one factory should not be mergeable with a heap from the other factory.

But what if I create two separate factories with the same element type and the same ordering? Should a heap created from one of these factories be mergeable with a heap created from the other factory?  It's not clear.  If this duplication of factories was deliberate, then the answer is probably "no".  This often happens with units of measure.  For example, maybe one of the factories is using integers to represent inches and the other is using integers to represent grams.  Even if the factories are using the same ordering, we probably don't want to merge a heap of inches with a heap of grams!

On the other hand, the duplication of factories could be accidental, perhaps the result of two chunks of code being written separately and then brought together later.  In that case, we might very well want to be able to merge a heap from one factory with a heap from another accidentally-separate-but-equivalent factory.

Regardless of where you come down on what _should_ happen, what _will_ happen in the above design is that attempting to merge heaps from distinct factories will cause a type error,
even if the factories were made for the same element type and ordering.

## Problem 4: Usability in the simple case

Most applications of priority queues do not need the `merge` method. Trying to make `merge` typesafe has made the API more complicated and harder to use because of the need to instantiate a factory before creating actual heaps.  Can we hide these complications from a user until and unless they actually need to use `merge`?  Yes.

I'll re-introduce the interface without `merge`, but now called `SHeap` for "Simple Heap".
```scala
trait SHeap[Elem] {
  def isEmpty: Boolean
  def add(elem: Elem): SHeap[Elem]
  def first: Elem
  def rest: SHeap[Elem]
}
```
Then `MHeap` should be a subtype of `SHeap`.
```scala
trait MHeap[Elem, Heap <: SHeap[Elem]] extends SHeap[Elem] {
  // inherits isEmpty and first from SHeap[Elem]
  def add(elem: Elem): Heap // more specific return type
  def rest: Heap // more specific return type
  def merge(other: Heap): Heap
}
```
The `HeapFactory` definition is unchanged.
```scala
trait HeapFactory {
  type Elem
  type Heap <: MHeap[Elem,Heap]

  def empty: Heap
  // plus other factory methods
}
```
The last part is that the companion object should supply simple factory methods in terms of `SHeap`.
```scala
object LeftistHeap { // companion object
  def empty[E](implicit ord: Ordering[E]): SHeap[E] = ...
  // plus other ordinary factory methods, similar to other Scala collections

  // the big bad
  def factory[E](implicit ord: Ordering[E]): HeapFactory { type Elem = E } = ...
}
```
Now the user can proceed in blissful ignorance of `factory` or `MHeap`, treating this essentially just like any other Scala collection, until they need `merge`.  Of course, `empty` will probably be defined as
```scala
  def empty[E](implicit ord: Ordering[E]): SHeap[E] = factory[E](ord).empty
```
(and similarly for the other ordinary factory methods), but the user doesn't need to know that.

## Problem 5: min vs max

Should a heap favor smaller elements or bigger elements?
There's no obvious answer&mdash;applications abound for both.
Therefore, an interface should easily support both flavors. Right now, the
ordering parameter allows us to say
```scala
val minHeaps = LeftistHeap.factory[Int](Ordering.Int)
val maxHeaps = LeftistHeap.factory[Int](Ordering.Int.reverse)
```
But how did I know that `Ordering.Int` was the right ordering for
min-heaps and `Ordering.Int.reverse` was the right ordering for max-heaps?
The opposite could just as easily have been true. Sure, this detail would probably be
documented in the API, but it was fundamentally a flip-a-coin arbitrary decision.
And arbitrary decisions with no logic favoring one choice over the other are
the hardest to remember.

In an easier-to-use interface, the user might write
```scala
val minHeaps = LeftistHeap.minFactory[Int](Ordering.Int)
val maxHeaps = LeftistHeap.maxFactory[Int](Ordering.Int)
```
Now, the user doesn't need to worry whether to use `Ordering.Int` or
`Ordering.Int.reverse`.  Instead, if they want min-oriented heaps, they call
`minFactory(Ordering.Int)` and if they want max-oriented heaps, they call
`maxFactory(Ordering.Int)`.  In fact, it's even better than that.  The whole
point of implicit parameters is that you usually don't need to write them
down explicitly.  In reality, the user would probably only write
```scala
val minHeaps = LeftistHeap.minFactory[Int]
val maxHeaps = LeftistHeap.maxFactory[Int]
```
Actually in the current version, this is now
```scala
val minHeaps = LeftistHeap.Min.factory[Int]
val maxHeaps = LeftistHeap.Max.factory[Int]
```
where `LeftistHeap.Min` and `LeftistHeap.Max` both support other
simpler methods for creating `SHeap`s for users who don't need `merge`.
For example, a user could write
```scala
val h1 = LeftistHeap.Min.empty[Int] // an empty min-heap of integers
val h2 = LeftistHeap.Max(1,2,3) // a max-heap containing 1, 2, and 3
```

_Of course, there's lots more needed to flesh the whole design out into an
industrial-strength API, and even more to integrate it with the current
Scala collections. I'll continue to work on this, and I welcome discussion on
these issues._
