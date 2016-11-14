/*
 * Copyright (c) 2016 Tom Bocklisch <https://github.com/tmbo>
 */

package de.hpi.kdd.rar.util

import com.typesafe.scalalogging.Logger

/**
  * Helper class to measure execution times
  */
trait TimeMeasurement {

  protected def logger: Logger

  /**
    * Measure the runtime of a function and print the result to an output logger
    *
    * @param name function name to use for printing
    * @param f    function to measure runtime of
    * @tparam A result type of f
    * @return result of the execution of f
    */
  def timeMeasured[A](name: String)(f: => A): A = {
    val (r, time) = timeIt(f)
    logger.debug(s"Executing $name took $time ms")
    r
  }

  /**
    * Measure and return runtime for a function
    *
    * @param f function to measure
    * @tparam A result type of f
    * @return tuple of the result of f and the runtime
    */
  def timeIt[A](f: => A): (A, Long) = {
    val s = System.currentTimeMillis()
    val r = f
    val t = System.currentTimeMillis()
    (r, t - s)
  }
}
