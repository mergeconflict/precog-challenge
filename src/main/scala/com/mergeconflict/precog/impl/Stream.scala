package com.mergeconflict.precog.impl

import java.io.{ InputStream, OutputStream }
import scala.collection.immutable.{ SortedMap, SortedSet }

// Type class to abstract operations on InputStream and OutputStream
trait Stream[A] {
  def get(in: InputStream): A
  def put(value: A, out: OutputStream): Unit
}

object Stream {

  // Extension method for InputStream to use Streams type class instances
  class InputStreamOps(in: InputStream) {
    def get[A: Stream]: A = implicitly[Stream[A]].get(in)
  }
  implicit def toInputStreamOps(in: InputStream) = new InputStreamOps(in)

  // Extension method for OutputStream to use Streams type class instances
  class OutputStreamOps(out: OutputStream) {
    def put[A: Stream](value: A): Unit = implicitly[Stream[A]].put(value, out)
  }
  implicit def toOutputStreamOps(out: OutputStream) = new OutputStreamOps(out)
  
  // Stream instance for Byte: one byte in the stream
  implicit val byteInstance = new Stream[Byte] {
    def get(in: InputStream): Byte = in.read().toByte
    def put(value: Byte, out: OutputStream): Unit = out.write(value.toInt)
  }

  // Stream instance for Int: four bytes in the stream
  implicit val intInstance = new Stream[Int] {
    def get(in: InputStream): Int =
      in.read() << 24 | in.read() << 16 | in.read() << 8 | in.read()
    def put(value: Int, out: OutputStream): Unit = {
      out.write(value >>> 24)
      out.write(value >>> 16)
      out.write(value >>> 8)
      out.write(value)
    }
  }

  // Stream instance for Array[Byte]: four bytes for an Int size, followed by that number of bytes
  implicit val byteArrayInstance = new Stream[Array[Byte]] {
    def get(in: InputStream): Array[Byte] = {
      val size = in.get[Int]
      val bytes = Array.ofDim[Byte](size)
      in.read(bytes)
      bytes
    }

    def put(value: Array[Byte], out: OutputStream): Unit = {
      out.put(value.length)
      out.write(value)
    }
  }

  // Stream instance for Option[A]: one byte for a some/none flag, followed by an optional value
  implicit def optionInstance[A: Stream] = new Stream[Option[A]] {
    def get(in: InputStream): Option[A] = in.get[Byte] match {
      case 0 => None
      case 1 => Some(in.get[A])
    }

    def put(value: Option[A], out: OutputStream): Unit = value match {
      case None => out.put(0: Byte)
      case Some(value) => {
        out.put(1: Byte)
        out.put(value)
      }
    }
  }

  // Stream instance for SortedSet[A]: four Stream for an Int size, followed by that number of entries
  implicit def sortedSetInstance[A: Stream: Ordering] = new Stream[SortedSet[A]] {
    def get(in: InputStream): SortedSet[A] =
      SortedSet(1 to in.get[Int] map { _ => in.get[A] }: _*)

    def put(value: SortedSet[A], out: OutputStream): Unit = {
      out.put(value.size)
      value foreach { out.put(_) }
    }
  }

  // Stream instance for (A, B)
  implicit def tuple2Instance[A: Stream, B: Stream] = new Stream[(A, B)] {
    def get(in: InputStream): (A, B) =
      in.get[A] -> in.get[B]

    def put(value: (A, B), out: OutputStream): Unit = {
      out.put(value._1)
      out.put(value._2)
    }
  }

  // Stream instance for SortedMap[K, V]: four Stream for an Int size, followed by that number of key-value pairs
  implicit def sortedMapInstance[K: Stream: Ordering, V: Stream] = new Stream[SortedMap[K, V]] {
    def get(in: InputStream): SortedMap[K, V] =
      SortedMap(1 to in.get[Int] map { _ => in.get[(K, V)] }: _*)

    def put(value: SortedMap[K, V], out: OutputStream): Unit = {
      out.put(value.size)
      value foreach { out.put(_) }
    }
  }

}