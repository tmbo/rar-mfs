package de.hpi.kdd.rar.util

import scala.collection.mutable

/**
  * Helper functions to calculate simple distributions
  */
object Statistics {
  /**
    * Create a discrete distribution, i.e. count the occurence of every value
    * @param valuesForeach iterator over the values
    * @param numDifferentValues value range
    * @return distribution of the values
    */
  def valueDistribution(valuesForeach: (Double => Unit) => Unit, numDifferentValues: Int): mutable.Map[Double, Int] = {
    val counts = mutable.HashMap.empty[Double, Int]

    valuesForeach { value: Double =>
      // TODO: think about how to handle missing values (currently they simply get ignored)
      if (!value.isNaN) {
        if(counts.contains(value)){
          counts.put(value, counts(value) + 1)
        } else {
          counts.put(value, 1)
        }
      }
    }
    counts
  }

  /**
    * Create a discrete distribution, i.e. count the occurrence of every value
    * @param values values
    * @param numDifferentValues number of different values in `values`
    * @return distribution of the values
    */
  def valueDistribution(values: Array[Double], numDifferentValues: Int): mutable.Map[Double, Int] = {
    valueDistribution({f => values.foreach(f)}, numDifferentValues)
  }
}
