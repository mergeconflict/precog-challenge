package com.mergeconflict.precog

trait DiskStore {
  def put(key: Array[Byte], value: Array[Byte]): Unit
  def get(key: Array[Byte]): Option[Array[Byte]]
  def flush(): Unit
  def traverse[A](start: Array[Byte], end: Array[Byte])(reader: Reader[A]): A
}

trait DiskStoreSource {
  def apply(name: String): DiskStore
}