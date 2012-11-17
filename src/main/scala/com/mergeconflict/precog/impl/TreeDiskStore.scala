package com.mergeconflict.precog.impl

import java.io.{ InputStream, OutputStream }
import java.nio.file.{ Files, Paths }

import scala.collection.immutable.{ SortedMap, SortedSet }
import scala.collection.mutable.WeakHashMap

import com.mergeconflict.bplus._
import com.mergeconflict.precog._

object TreeDiskStoreProvider {
  import Stream._
  import Id._
  import Node._

  implicit def idStreamInstance[K: Stream: Identity] = new Stream[Id[K]] {
    def get(in: InputStream): Id[K] = in.get[Byte] match {
      case 0 => UpperBound(in.get[K], in.get[Int])
      case 1 => Infinity(in.get[Int])
    }
    def put(value: Id[K], out: OutputStream): Unit = value match {
      case UpperBound(key, depth) => {
        out.put(0: Byte)
        out.put(key)
        out.put(depth)
      }
      case Infinity(depth) => out.put(depth)
    }
  }

  implicit def nodeStreamInstance[K: Stream: Identity: Ordering, V: Stream] = new Stream[Node[K, V]] {
    def get(in: InputStream): Node[K, V] = in.get[Byte] match {
      case 0 => DataNode(in.get[Id[K]], in.get[SortedMap[K, V]], in.get[Option[Id[K]]])
      case 1 => TreeNode(in.get[Id[K]], in.get[SortedSet[Id[K]]])
    }
    def put(value: Node[K, V], out: OutputStream): Unit = value match {
      case DataNode(id, data, next) => {
        out.put(0: Byte)
        out.put(id)
        out.put(data)
        out.put(next)
      }
      case TreeNode(id, children) => {
        out.put(1: Byte)
        out.put(id)
        out.put(children)
      }
    }
  }

  import sun.misc.BASE64Encoder
  def filename(id: Id[Array[Byte]]) = id match {
    case UpperBound(key, depth) => (new BASE64Encoder).encode(key).replace('/', '-') + '.' + depth
    case Infinity(depth) => "infinity." + depth
  }
}
class TreeDiskStoreProvider(name: String) extends MutableProvider[Array[Byte], Array[Byte]] {
  import Stream._
  import TreeDiskStoreProvider._

  private val cache = new WeakHashMap[Id[Array[Byte]], Node[Array[Byte], Array[Byte]]]
  private val path = Paths.get(System.getProperty("user.dir"), "data", name)

  def b: Int = 50

  def initialize: Option[Node[Array[Byte], Array[Byte]]] = try {
    val in = Files.newInputStream(path.resolve("index"))
    try {
      val id = in.get[Id[Array[Byte]]]
      Some(load(id))
    } finally {
      in.close()
    }
  } catch {
    case _ => None
  }

  def load(id: Id[Array[Byte]]): Node[Array[Byte], Array[Byte]] =
    cache.getOrElseUpdate(id, {
      val in = Files.newInputStream(path.resolve(filename(id)))
      try {
        in.get[Node[Array[Byte], Array[Byte]]]
      } finally {
        in.close()
      }
    })

  def flush(tree: Tree[Array[Byte], Array[Byte]]): Unit = {
    val directory = Files.createDirectories(path)
    val out = Files.newOutputStream(path.resolve("index"))
    try {
      out.put(tree.root.id)
    } finally {
      out.close()
    }
    for ((id, node) <- tree.dirty) {
      cache.put(id, node)
      val out = Files.newOutputStream(path.resolve(filename(id)))
      try {
        out.put(node)
      } finally {
        out.close()
      }
    }
  }
}

class TreeDiskStore(name: String) extends MutableTree[Array[Byte], Array[Byte]](new TreeDiskStoreProvider(name)) with DiskStore {
  def traverse[A](start: Array[Byte], end: Array[Byte])(reader: Reader[A]): A = {
    import Node._, annotation._

    @tailrec
    def go(it: Iterator[(Array[Byte], Array[Byte])], next: Option[Id[Array[Byte]]], reader: Reader[A]): A = reader match {
      case More(fn) => {
        if (it.hasNext) {
          val (key, value) = it.next()
          if (byteArrayOrdering.compare(key, end) <= 0)
            go(it, next, fn(Some(key -> value)))
          else
            go(it, next, fn(None))
        } else next match {
          case Some(id) => {
            val DataNode(_, data, next) = provider.load(id)
            go(data.iterator, next, reader)
          }
          case None => go(it, next, fn(None))
        }
      }
      case Done(value) => value
    }

    val node = tree.locate(start)
    val data = node.data.from(start)
    go(data.iterator, node.next, reader)
  }
}

class TreeDiskStoreSource extends DiskStoreSource {
  def apply(name: String): TreeDiskStore = new TreeDiskStore(name)
}