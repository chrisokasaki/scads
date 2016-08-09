import scads.immutable._
import org.scalacheck._

object LeftistHeapTest extends Properties("LeftistHeap") with HeapTest {
  def companion: HeapCompanion = LeftistHeap
}
