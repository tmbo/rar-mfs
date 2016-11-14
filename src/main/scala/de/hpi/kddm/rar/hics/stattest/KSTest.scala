/*
 * Copyright (C) Tom Bocklisch <https://github.com/tmbo>
 */
package de.hpi.kdd.rar.hics.stattest

import java.util.Random

import com.typesafe.scalalogging.LazyLogging
import de.hpi.kdd.rar.dataset.{ContinuousFeature, SubSpace}
import de.hpi.kdd.rar.hics.IndexSelection

case class KSTest(
                   N: Int,
                   M: Int,
                   expectedMinDev: Double,
                   expectedMaxDev: Double) extends StatTest[ContinuousFeature]{

  def computeDeviation(
                        subSpace: SubSpace,
                        feature: ContinuousFeature,
                        selection: IndexSelection,
                        shouldNormalize: Boolean): Double = {

    val numRemainingObjects = selection.M
    val chosenElements = selection.mapSelected(i => feature.sortedIndices(i)).sorted
    var cumulatedDistTest = 0.0

    var i = 0
    var maxDiscrepancy = Double.NegativeInfinity

    while(i <= chosenElements.length){
      val ii = if(i < chosenElements.length) chosenElements(i) else N

      val cumulatedDistOrigBefore = ii.toDouble / N
      maxDiscrepancy = math.max(maxDiscrepancy, math.abs(cumulatedDistTest - cumulatedDistOrigBefore))

      cumulatedDistTest = (i+1).toDouble / numRemainingObjects
      val cumulatedDistOrigAfter = (ii+1).toDouble / N
      maxDiscrepancy = math.max(maxDiscrepancy, math.abs(cumulatedDistTest - cumulatedDistOrigAfter))
      i += 1
    }

    if (shouldNormalize)
      math.max(0.0, math.min((maxDiscrepancy - expectedMinDev) / (expectedMaxDev - expectedMinDev), 1.0))
    else
      maxDiscrepancy
  }
}

object KSTest extends LazyLogging{
  val seed = 42

  private def computeDeviationFromSelfSelection(selection: IndexSelection): Double = {
    // This function is used internally for the calibration of the KS test.
    // It does almost the same as the final `computeDeviation` function,
    // but does not operate on actual data. Instead is considers cases
    // of "self selection", which only depend on N and M (obtained from the selection)
    // and not on the data.
    var cumulatedDistTest = 0.0
    var maxDiscrepancy = Double.NegativeInfinity
    var i = 0

    while (i < selection.N) {
      val cumulatedDistOrig = (i + 1).toDouble / selection.N

      if (selection.contains(i))
        cumulatedDistTest += 1.0 / selection.M

      maxDiscrepancy = math.max(maxDiscrepancy, math.abs(cumulatedDistTest - cumulatedDistOrig))
      i += 1
    }

    maxDiscrepancy
  }

  private def determineExpectedMinDeviation(N: Int, M: Int, iterations: Int): Double = {
    // min deviation == case of fully independent selection
    val selection = IndexSelection(N, new Random(seed))

    val totalDev = (0 until iterations).map { i =>
      selection.selectRandomly(M)
      computeDeviationFromSelfSelection(selection)
    }.sum

    totalDev / iterations
  }

  private def determineExpectedMaxDeviation(N: Int, M: Int): Double = {
    // max deviation == case of fully dependent block selection
    val selection = IndexSelection(N, new Random(seed + 1))
    val (min, max, numPossible) = selection.possibleOffsets(M)
    val totalDev = (min to max).map { offset =>
      math.max(offset.toDouble / N, 1 - (M + offset).toDouble / N)
    }.sum

    totalDev / numPossible
  }

  def init(
            subSpace: SubSpace,
            expectedSampleSize: Int,
            calibrationIterations: Int = 100): KSTest = {
    val N = subSpace.numberOfInstances
    val M = expectedSampleSize

    assert(M > 0, "expectedSampleSize must be larger than zero.")
    assert(M < N, "expectedSampleSize must be smaller than the total sample size.")

    val expectedMinDev = determineExpectedMinDeviation(N, M, calibrationIterations)
    logger.debug("KS-Test min dev finished. Min: " + expectedMinDev)
    val expectedMaxDev = determineExpectedMaxDeviation(N, M)
    logger.debug("KS-Test max dev finished. Max: " + expectedMaxDev)

    KSTest(N, M,
      expectedMinDev,
      expectedMaxDev)
  }
}