// TODO: Needs more tests!
// TODO: Make infrastructure so can reuse tests between different kinds of
//   heaps (leftist, pairing, ...)

import scads.immutable._
import org.scalacheck._
import Prop._
import Arbitrary._

object LeftistHeapTest extends Properties("LeftistHeap") {

  type E = Int // the element type used for most/all of the tests
  val minFactory = LeftistHeap.minFactory[E]
  val maxFactory = LeftistHeap.maxFactory[E]

  property("minFactory.apply") = forAll { list: List[E] =>
    val h = minFactory(list : _*)
    h.checkInvariant()
    h.sorted.toList == list.sorted
  }
  property("maxFactory.apply") = forAll { list: List[E] =>
    val h = maxFactory(list : _*)
    h.checkInvariant()
    h.sorted.toList == list.sorted.reverse
  }
  property("isEmpty/size (minFactory)") = forAll {list: List[E] =>
    val h = minFactory(list : _*)
    h.checkInvariant()
    h.isEmpty == list.isEmpty && h.size == list.size
  }
  property("isEmpty/size (maxFactory)") = forAll {list: List[E] =>
    val h = maxFactory(list : _*)
    h.checkInvariant()
    h.isEmpty == list.isEmpty && h.size == list.size
  }
  property("add (minFactory)") = forAll { (list: List[E], x: E) =>
    val h = minFactory(list : _*)
    val h2 = h.add(x)
    h2.checkInvariant()
    h2.sorted.toList == (x +: list).sorted
  }
  property("add (maxFactory)") = forAll { (list: List[E], x: E) =>
    val h = maxFactory(list : _*)
    val h2 = h.add(x)
    h2.checkInvariant()
    h2.sorted.toList == (x +: list).sorted.reverse
  }
  property("first (minFactory)") = forAll { list: List[E] =>
    val h = minFactory(list : _*)
    val fst = scala.util.Try(h.first)
    if (list.isEmpty) fst.isFailure
    else fst.isSuccess && fst.get == list.min
  }
  property("first (maxFactory)") = forAll { list: List[E] =>
    val h = maxFactory(list : _*)
    val fst = scala.util.Try(h.first)
    if (list.isEmpty) fst.isFailure
    else fst.isSuccess && fst.get == list.max
  }
  property("rest (minFactory)") = forAll { list: List[E] =>
    val h = minFactory(list : _*)
    val r = scala.util.Try(h.rest)
    if (list.isEmpty) r.isFailure
    else r.isSuccess && r.get.sorted.toList == list.sorted.tail
  }
  property("rest (maxFactory)") = forAll { list: List[E] =>
    val h = maxFactory(list : _*)
    val r = scala.util.Try(h.rest)
    if (list.isEmpty) r.isFailure
    else r.isSuccess && r.get.sorted.toList == list.sorted.reverse.tail
  }
  property("merge (minFactory)") = forAll { (list1: List[E], list2: List[E]) =>
    val h1 = minFactory(list1 : _*)
    val h2 = minFactory(list2 : _*)
    val merged = h1.merge(h2)
    merged.sorted.toList == (list1 ++ list2).sorted
  }
  property("merge (maxFactory)") = forAll { (list1: List[E], list2: List[E]) =>
    val h1 = maxFactory(list1 : _*)
    val h2 = maxFactory(list2 : _*)
    val merged = h1.merge(h2)
    merged.sorted.toList == (list1 ++ list2).sorted.reverse
  }

}
