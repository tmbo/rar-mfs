package de.hpi.kddm.rar.util

import scala.collection.parallel.ForkJoinTaskSupport
import scala.util.Random

object ImplicitExtensions {

  implicit class IntWithCombinations(n: Int) {
    /**
      * Combinatorial helper. Calculates: n! / ((n-k)!*k!)
      */
    def choose(k: Int) = {
      def chooseRec(n: Int, k: Int): Long = {
        if (k == 0) 1
        else (n * chooseRec(n - 1, k - 1)) / k
      }
      chooseRec(n, k)
    }
  }

  implicit class RichParArray[A](xs: Array[A]) {
    /**
      * Create a parallel array with a fixed size thread pool
      *
      * @param p maximum number of threads
      * @return par array with attached thread pool
      */
    def parWithMaxParallelism(p: Int) = {
      val parArray = xs.par
      parArray.tasksupport = new ForkJoinTaskSupport(
        new scala.concurrent.forkjoin.ForkJoinPool(p)
      )
      parArray
    }
  }

  implicit class RichArray[A](xs: Array[A]) {
    /**
      * Calculate the average of the list. Default is 0.0
      */
    def average(implicit num: Numeric[A]): Double = {
      if (xs.isEmpty) 0.0
      else num.toDouble(xs.sum) / xs.length
    }

    /**
      * Find the index of the element that has the maximum value
      *
      * @param cmp ordering
      * @return index of element with maximum value
      */
    def indexOfMax(implicit cmp: Ordering[A]): Int = {
      xs.zipWithIndex.maxBy(_._1)._2
    }

    /**
      * Randomize the elements of an array (not in place)
      *
      * @param rand random number generator to be used
      * @return copy of original array with elements in random order
      */
    def shuffle(rand: Random) = {
      val buf = xs.clone()

      def swap(i1: Int, i2: Int) {
        val tmp = buf(i1)
        buf(i1) = buf(i2)
        buf(i2) = tmp
      }

      for (n <- buf.length to 2 by -1) {
        val k = rand.nextInt(n)
        swap(n - 1, k)
      }
      buf
    }
  }

}
