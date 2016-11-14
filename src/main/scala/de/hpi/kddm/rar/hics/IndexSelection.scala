/*
 * Copyright (C) Tom Bocklisch <https://github.com/tmbo>
 */
package de.hpi.kdd.rar.hics

import java.util

import com.typesafe.scalalogging.LazyLogging
import de.hpi.kdd.rar.dataset.{ContinuousFeature, Feature, NominalFeature}
import de.hpi.kdd.rar.util.ImplicitExtensions._

import scala.reflect.ClassTag
import scala.util.Random

object IndexSelection {
  def apply(N: Int, rand: Random = new Random): IndexSelection =
    new IndexSelection(N, rand, new util.BitSet(N))
}

class IndexSelection(val N: Int, rand: Random, val underlying: util.BitSet) extends LazyLogging{

  def M = underlying.cardinality()

  def and(isel: IndexSelection) = {
    assert(isel.N == N, "Can only calculate `and` between equally sized index selections")
    underlying.and(isel.underlying)
    this
  }

  def mappedUsing(mapped: Array[Int]) = {
    val mappedBitSet = new util.BitSet(N)
    withSelected(i => mappedBitSet.set(mapped(i)))
    new IndexSelection(N, rand, mappedBitSet)
  }

  def possibleOffsets(M: Int): (Int, Int, Int) = {
    val numPossible = N - M + 1
    // min, max, numPossible
    (0, numPossible - 1, numPossible)
  }

  def contains(i: Int): Boolean = {
    underlying.get(i)
  }

  def reset(b: Boolean): Unit = {
    underlying.set(0, N, b)
  }

  def selectBlock(M: Int, offset: Int) = {
    val (min, max, _) = possibleOffsets(M)
    if (offset >= min && offset <= max) {
      underlying.clear()
      underlying.set(offset, offset + M, true)
    } else {
      throw new IllegalArgumentException(s"Can't select block at offset $offset. Min = $min Max = $max")
    }
  }

  def unset(i: Int) =
    underlying.clear(i)

  @inline
  def withUnselected(f: Int => Unit): Unit = {
    var i = underlying.nextClearBit(0)
    while (i >= 0 && i < N) {
      f(i)
      i = underlying.nextClearBit(i + 1)
    }
  }

  @inline
  def withSelected(f: Int => Unit): Unit = {
    var i = underlying.nextSetBit(0)
    while (i >= 0) {
      f(i)
      i = underlying.nextSetBit(i + 1)
    }
  }

  @inline
  def mapSelected[T](f: Int => T)(implicit evidence: ClassTag[T]): Array[T] = {
    val r = new Array[T](M)
    var i = underlying.nextSetBit(0)
    var ridx = 0
    while (i >= 0) {
      r(ridx) = f(i)
      i = underlying.nextSetBit(i + 1)
      ridx += 1
    }
    r
  }

  def selectRandomBlockNominal(M: Int, feature: NominalFeature): Unit = {
    val valueDist = feature.valueDist
    val shuffledIdx = valueDist.shuffle(new Random(rand.nextInt))
    var slidingSum = 0
    var i = 0

    underlying.clear()

    while(slidingSum < M && i < shuffledIdx.length) {
      val (value, occurrences) = shuffledIdx(i)

      try {
        underlying.or(feature.valueIndex(value))
      } catch {
        case e: Exception =>
          logger.error("NOOO: " + e.getMessage)
          e.printStackTrace()
          throw e
      }

      slidingSum += occurrences
      i += 1
    }
  }

  def selectRandomBlockContinuous(M: Int, feature: ContinuousFeature): Unit = {
    val (_, max, _) = possibleOffsets(M)
    val offset = rand.nextInt(max + 1)

    underlying.clear()

    val sortedValues = feature.sortedValues

    var i = offset
    // Important: This is an important piece of the continuous selection. If there are multiple samples with the same
    // value, we need to either select them all or select none. If the selected offset is in the middle of a block of
    // samples with the same value, we need to select the samples before the start offset with the same values as well.
    // Same holds for the end.
    var j = offset - 1
    var equalValue = true
    val firstValue = feature.values(sortedValues(i))
    while(j >= 0 && equalValue){
      val idx = sortedValues(j)
      if(firstValue == feature.values(idx))
        underlying.set(idx)
      else
        equalValue = false
      j -= 1
    }

    val end = M + offset
    while(i < end){
      underlying.set(sortedValues(i))
      i += 1
    }

    // Select all samples that have the same value as the last sample of the selected block. Ensures correct
    // probabilistic estimation.
    val lastValue = feature.values(sortedValues(i-1))
    equalValue = true
    while(i < N && equalValue){
      val idx = sortedValues(i)
      if(lastValue == feature.values(idx))
        underlying.set(idx)
      else
        equalValue = false
      i += 1
    }
  }

  def selectRandomBlock(M: Int, feature: Feature): Unit = {
    feature match {
      case f: NominalFeature =>
        selectRandomBlockNominal(M, f)
      case f: ContinuousFeature =>
        selectRandomBlockContinuous(M, f)
    }
  }

  def selectRandomly(M: Int): Unit = {
    // Sampling without replacement, based on Algorithm 3.4.2S of Knuth's book Seminumeric Algorithms.
    // http://stackoverflow.com/questions/311703/algorithm-for-sampling-without-replacement/311716#311716
    reset(false)
    var t = 0 // total input records dealt with
    var m = 0 // number of items selected so far

    while (m < M) {
      val u = rand.nextDouble()

      if ((N - t) * u >= M - m)
        t += 1
      else {
        underlying.set(t)
        t += 1
        m += 1
      }
    }
  }
}
