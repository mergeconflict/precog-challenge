package com.mergeconflict.precog.impl

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.collection.immutable.{ SortedMap, SortedSet }

import org.junit.runner.RunWith
import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class StreamSpec extends Specification with ScalaCheck {

  import Stream._
  import Arbitrary._

  def roundtrip[A: Stream](expected: A) {
    val out = new ByteArrayOutputStream
    out.put(expected)
    val in = new ByteArrayInputStream(out.toByteArray())
    val actual = in.get[A]
    expected mustEqual actual
  }

  implicit def arbSortedSet[A: Arbitrary: Ordering]: Arbitrary[SortedSet[A]] =
    Arbitrary(arbitrary[List[A]] map { SortedSet(_: _*) })

  implicit def arbSortedMap[K: Arbitrary: Ordering, V: Arbitrary]: Arbitrary[SortedMap[K, V]] =
    Arbitrary(arbitrary[List[(K, V)]] map { SortedMap(_: _*) })

  "Bytes" should {
    "encode/decode Int" in check { expected: Int => roundtrip(expected) }
    "encode/decode Array[Byte]" in check { expected: Array[Byte] => roundtrip(expected) }
    "encode/decode Option[A]" in check { expected: Option[Int] => roundtrip(expected) }
    "encode/decode SortedSet[A]" in check { expected: SortedSet[Int] => roundtrip(expected) }
    "encode/decode (A, B)" in check { expected: (Int, Int) => roundtrip(expected) }
    "encode/decode SortedMap[K, V]" in check { expected: SortedMap[Int, Int] => roundtrip(expected) }
  }

}