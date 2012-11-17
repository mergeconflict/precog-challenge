package com.mergeconflict.precog

import com.mergeconflict.bplus.Identity

package object impl {
  implicit def byteArrayIdentity = new Identity[Array[Byte]] {
    def equals(lhs: Array[Byte], rhs: Array[Byte]): Boolean = lhs.toSeq == rhs.toSeq
    def hashCode(value: Array[Byte]): Int = value.toSeq.hashCode
  }

  implicit def byteArrayOrdering = new Ordering[Array[Byte]] {
    def compare(lhs: Array[Byte], rhs: Array[Byte]) = implicitly[Ordering[Iterable[Byte]]].compare(lhs, rhs)
  }
}