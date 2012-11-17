package com.mergeconflict.precog.impl

import scala.collection.immutable.SortedMap

import org.junit.runner.RunWith
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TreeDiskStoreSpec extends Specification with ScalaCheck {

  import org.scalacheck._

  case class StoreAndEntries(store: TreeDiskStore, entries: SortedMap[Array[Byte], Array[Byte]])

  def makeStore(name: String, entries: SortedMap[Array[Byte], Array[Byte]]): TreeDiskStore = {
    val store = (new TreeDiskStoreSource)(name)
    for ((key, value) <- entries)
      store.put(key, value)
    store.flush()
    store
  }

  import Arbitrary._, Gen._, Prop._

  "TreeDiskStores" should {
    "contain all the elements that are inserted" in forAll(
      alphaStr map { "test" + _ },
      listOf1(arbitrary[(Array[Byte], Array[Byte])]) map { SortedMap(_: _*) }) {
        (name: String, entries: SortedMap[Array[Byte], Array[Byte]]) =>
          val store = makeStore(name, entries)
          entries must haveAllElementsLike {
            case (key, value) => store.get(key) must beSome(value)
          }
      }

    "contain data in sorted order" in forAll(
      alphaStr map { "test" + _ },
      listOf1(arbitrary[(Array[Byte], Array[Byte])]) map { SortedMap(_: _*) }) {
        (name: String, entries: SortedMap[Array[Byte], Array[Byte]]) =>
          import com.mergeconflict.precog._

          val reader = new More({
            case Some((lhs, _)) => {
              def reader(lhs: Array[Byte]): Reader[Boolean] = new More({
                case Some((rhs, _)) =>
                  if (byteArrayOrdering.compare(lhs, rhs) < 0)
                    reader(rhs)
                  else
                    Done(false)
                case None => Done(true)
              })
              reader(lhs)
            }
            case None => Done(true)
          })

          val store = makeStore(name, entries)
          store.traverse(entries.head._1, entries.last._1)(reader) must beTrue
      }
  }
}