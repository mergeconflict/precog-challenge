package com.mergeconflict.bplus

import scala.collection.mutable.Map
import org.junit.runner.RunWith
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import scala.collection.immutable.SortedMap

@RunWith(classOf[JUnitRunner])
class TreeSpec extends Specification with ScalaCheck {

  import org.scalacheck._
  import Arbitrary._, Gen._, Prop._

  class InMemoryProvider extends MutableProvider[Int, Int] {
    import collection.mutable.Map
    private val nodes: Map[Id[Int], Node[Int, Int]] = Map.empty

    def b: Int = 4
    def initialize: Option[Node[Int, Int]] = None
    def load(id: Id[Int]): Node[Int, Int] = nodes(id)
    def flush(tree: Tree[Int, Int]): Unit =
      nodes ++= tree.dirty
  }

  implicit def intIdentity = new Identity[Int] {
    def equals(lhs: Int, rhs: Int): Boolean = lhs == rhs
    def hashCode(value: Int): Int = value
  }

  def makeTree(entries: Map[Int, Int]) = entries.foldLeft(Tree(new InMemoryProvider)) {
    case (tree, (key, value)) => tree.put(key, value)
  }

  "Trees" should {
    "contain all the elements that are inserted" in check { entries: Map[Int, Int] =>
      val tree = makeTree(entries)
      entries must haveAllElementsLike { case (key, value) => tree.get(key) must beSome(value) }
    }
  }

}