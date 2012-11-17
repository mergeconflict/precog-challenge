package com.mergeconflict

package object bplus {

  // type class for structural identity, necessary because Array uses reference identity
  trait Identity[A] {
    def equals(lhs: A, rhs: A): Boolean
    def hashCode(value: A): Int
  }

}