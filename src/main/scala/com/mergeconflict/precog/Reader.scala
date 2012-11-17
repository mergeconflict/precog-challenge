package com.mergeconflict.precog

sealed trait Reader[A]
case class More[A](value: Option[(Array[Byte], Array[Byte])] => Reader[A]) extends Reader[A]
case class Done[A](value: A) extends Reader[A]