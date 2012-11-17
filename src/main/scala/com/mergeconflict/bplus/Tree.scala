package com.mergeconflict.bplus

import collection.immutable.{ Map, SortedMap, SortedSet }

// provider interface for B+ trees
trait MutableProvider[K, V] {
  def b: Int
  def initialize: Option[Node[K, V]]
  def load(id: Id[K]): Node[K, V]
  def flush(tree: Tree[K, V]): Unit
}

// Id: either an upper bound or infinity, distinguished by tree depth (where leaves are depth 0)
sealed trait Id[+K] {
  def depth: Int
  def copy(depth: Int): Id[K]
}
object Id {
  case class UpperBound[K: Identity](key: K, depth: Int) extends Id[K] {
    def copy(depth: Int): Id[K] = UpperBound(key, depth)
    override def equals(other: Any): Boolean = other match {
      case that: UpperBound[K] => implicitly[Identity[K]].equals(this.key, that.key) && this.depth == that.depth
      case _ => false
    }
    override def hashCode: Int = 23 * (23 + implicitly[Identity[K]].hashCode(key)) + depth
  }
  case class Infinity(depth: Int) extends Id[Nothing] {
    def copy(depth: Int): Infinity = Infinity(depth)
  }

  // infinity is always greater than any upper bound. note that ids are only ever compared for ordering at equal depths
  implicit def idOrder[K: Ordering]: Ordering[Id[K]] = new Ordering[Id[K]] {
    def compare(lhs: Id[K], rhs: Id[K]): Int = (lhs, rhs) match {
      case (UpperBound(lhs, _), UpperBound(rhs, _)) => implicitly[Ordering[K]].compare(lhs, rhs)
      case (UpperBound(_, _), Infinity(_)) => -1
      case (Infinity(_), UpperBound(_, _)) => 1
      case (Infinity(_), Infinity(_)) => 0
    }
  }
}

// result type for put: either a no-op, a single node update, or a node split
sealed trait Put[K, V]
object Put {
  case class Noop[K, V](dirty: Map[Id[K], Node[K, V]]) extends Put[K, V]
  case class Update[K, V](node: Node[K, V], dirty: Map[Id[K], Node[K, V]]) extends Put[K, V]
  case class Split[K, V](lhs: Node[K, V], rhs: Node[K, V], dirty: Map[Id[K], Node[K, V]]) extends Put[K, V]
}

// Node: either a tree (internal) node or a data (leaf) node
sealed trait Node[K, V] {
  def id: Id[K]
  def locate(key: K, dirty: Map[Id[K], Node[K, V]], provider: MutableProvider[K, V]): Node.DataNode[K, V]
  def get(key: K, dirty: Map[Id[K], Node[K, V]], provider: MutableProvider[K, V]): Option[V]
  def put(key: K, value: V, dirty: Map[Id[K], Node[K, V]], provider: MutableProvider[K, V]): Put[K, V]
}

object Node {
  import Id._
  import Put._

  case class DataNode[K: Identity, V](id: Id[K], data: SortedMap[K, V], next: Option[Id[K]]) extends Node[K, V] {
    def locate(key: K, dirty: Map[Id[K], Node[K, V]], provider: MutableProvider[K, V]): DataNode[K, V] = this
    def get(key: K, dirty: Map[Id[K], Node[K, V]], provider: MutableProvider[K, V]): Option[V] = data.get(key)
    def put(key: K, value: V, dirty: Map[Id[K], Node[K, V]], provider: MutableProvider[K, V]): Put[K, V] = {
      import provider._

      val data = this.data + (key -> value)
      if (data == this.data) {
        Noop(dirty)
      } else if (data.size < b) {
        val node = DataNode(id, data, next)
        Update(node, dirty + (node.id -> node))
      } else {
        val (lhsData, rhsData) = data.splitAt(data.size / 2)
        val lhs = DataNode(UpperBound(lhsData.last._1, id.depth), lhsData, Some(id))
        val rhs = DataNode(id, rhsData, next)
        Split(lhs, rhs, dirty + (lhs.id -> lhs, rhs.id -> rhs))
      }
    }
  }

  case class TreeNode[K: Identity, V](id: Id[K], children: SortedSet[Id[K]]) extends Node[K, V] {
    private[this] def leastUpperBound(key: K, dirty: Map[Id[K], Node[K, V]], provider: MutableProvider[K, V]): Node[K, V] = {
      val childId = children.from(UpperBound(key, id.depth)).head
      dirty.getOrElse(childId, provider.load(childId))
    }
    def locate(key: K, dirty: Map[Id[K], Node[K, V]], provider: MutableProvider[K, V]): DataNode[K, V] =
      leastUpperBound(key, dirty, provider).locate(key, dirty, provider)
    def get(key: K, dirty: Map[Id[K], Node[K, V]], provider: MutableProvider[K, V]): Option[V] =
      leastUpperBound(key, dirty, provider).get(key, dirty, provider)
    def put(key: K, value: V, dirty: Map[Id[K], Node[K, V]], provider: MutableProvider[K, V]): Put[K, V] = {
      import provider._

      leastUpperBound(key, dirty, provider).put(key, value, dirty, provider) match {
        case noop @ Noop(_) => noop
        case Update(_, dirty) => Noop(dirty)
        case Split(lhs, rhs, dirty) => {
          val children = this.children + lhs.id + rhs.id
          if (children.size <= b) {
            val node = TreeNode[K, V](id, children)
            Update(node, dirty + (node.id -> node))
          } else {
            val (lhsChildren, rhsChildren) = children.splitAt(children.size / 2)
            val lhs = TreeNode[K, V](lhsChildren.last.copy(id.depth), lhsChildren)
            val rhs = TreeNode[K, V](id, rhsChildren)
            Split(lhs, rhs, dirty + (lhs.id -> lhs, rhs.id -> rhs))
          }
        }
      }
    }
  }
}

case class Tree[K: Identity: Ordering, V](root: Node[K, V], dirty: Map[Id[K], Node[K, V]], provider: MutableProvider[K, V]) {
  import Id._, Node._, Put._

  def locate(key: K): DataNode[K, V] = root.locate(key, dirty, provider)
  def get(key: K): Option[V] = root.get(key, dirty, provider)
  def put(key: K, value: V): Tree[K, V] = {
    import provider._

    root.put(key, value, dirty, provider) match {
      case Noop(dirty) => Tree(root, dirty, provider)
      case Update(root, dirty) => Tree(root, dirty, provider)
      case Split(lhs, rhs, dirty) => {
        val root = TreeNode[K, V](Infinity(this.root.id.depth + 1), SortedSet(lhs.id, rhs.id))
        Tree(root, dirty + (root.id -> root), provider)
      }
    }
  }
  def flush: Tree[K, V] = {
    provider.flush(this)
    Tree(root, Map.empty, provider)
  }
}
object Tree {
  import Id._
  import Node._
  def apply[K: Identity: Ordering, V](provider: MutableProvider[K, V]): Tree[K, V] =
    Tree(provider.initialize.getOrElse(DataNode(Infinity(0), SortedMap.empty, None)), Map.empty, provider)
}

class MutableTree[K: Identity: Ordering, V](protected val provider: MutableProvider[K, V]) {
  protected var tree = Tree(provider)
  def put(key: K, value: V): Unit = tree = tree.put(key, value)
  def get(key: K): Option[V] = tree.get(key)
  def flush(): Unit = tree = tree.flush
}