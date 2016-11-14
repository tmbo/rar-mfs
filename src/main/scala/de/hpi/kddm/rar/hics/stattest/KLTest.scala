/*
 * Copyright (c) 2016 Tom Bocklisch <https://github.com/tmbo>
 */

package de.hpi.kdd.rar.hics.stattest

import com.typesafe.scalalogging.LazyLogging
import de.hpi.kdd.rar.dataset.{NominalFeature, SubSpace}
import de.hpi.kdd.rar.hics.IndexSelection
import de.hpi.kdd.rar.util.{Statistics, TimeMeasurement}

/**
  * Use the Kullback–Leibler divergence to compute the differences in value distributions between marginal and
  * dependent probability distribution.
  */
object KLTest extends StatTest[NominalFeature] with TimeMeasurement with LazyLogging{
  val epsilon = 0.001

  def computeDeviation(
                        subSpace: SubSpace,
                        feature: NominalFeature,
                        selection: IndexSelection,
                        shouldNormalize: Boolean): Double = {
    val expected = feature.valueDist.toMap
    val featureValues = feature.values

    // Estimate the expected value distribution
    val totalExpected = feature.values.size + epsilon * feature.attribute.numValues
    val normalizedExpected = expected.mapValues(e => (epsilon + e) / totalExpected)

    // Estimate the observed value distribution in the selection
    val observed = Statistics.valueDistribution({f => selection.withSelected( i => f(featureValues(i)))}, feature.attribute.numValues)

    val totalObserved = selection.M + epsilon * feature.attribute.numValues
    val normalizedObserved = observed.mapValues(o => (epsilon + o) / totalObserved)

    // Calculate the KLD
    val kld = KLDivergence(normalizedObserved, normalizedExpected, totalObserved)

    if(shouldNormalize)
      normalize(kld)
    else
      kld
  }

  private def KLDivergence(Q: scala.collection.Map[Double, Double], P: scala.collection.Map[Double, Double], totalObserved: Double): Double = {
    P.map{
      case (idx, pi) =>
        pi * math.log(pi / Q.getOrElse(idx, epsilon / totalObserved))
    }.sum
  }
}
