package de.hpi.kdd.rar.hics

import com.typesafe.scalalogging.LazyLogging
import de.hpi.kdd.rar.dataset.{Feature, SubSpace}
import de.hpi.kdd.rar.hics.stattest.StatTest

object HiCSContrastMeasure extends LazyLogging {
  def calculateContrast[F <: Feature](
                         subspace: SubSpace,
                         compareAttribute: F,
                         params: HiCSContrastParams,
                         statTest: StatTest[F],
                         shouldNormalize: Boolean): Double = {

    val D = subspace.dimensions.size + 1
    val N = subspace.numberOfInstances
    val M = math.max((math.pow(params.alpha(N), 1.0 / (D - 1)) * N).toInt, 5)

    val iselAll = IndexSelection(N)
    val iselCur = IndexSelection(N)

    val maxIterations = (params.numIterations * params.maxRetries).toInt

    val totalDev = (0 until maxIterations).toStream.flatMap { iter =>
      iselAll.reset(true)

      subspace.features.foreach {
        case (idx, feature) =>
          if (idx != compareAttribute) {
            iselCur.selectRandomBlock(M, feature)

            iselAll.and(iselCur)
          }
      }

      if (iselAll.M == 0) {
        logger.trace(s"EMPTY SELECTION! For $compareAttribute wrt ${subspace.dimensions}")
        None
      } else {
        Some(statTest.computeDeviation(subspace, compareAttribute, iselAll, shouldNormalize))
      }
    }.take(params.numIterations).toList
    if (totalDev.isEmpty) {
      logger.error("Failed to calculate contrast due to empty statests")
      0.0
    } else {
      if (totalDev.length < params.numIterations)
        logger.trace(s"Underfilled stat test! For $compareAttribute wrt ${subspace.dimensions}. iters=${totalDev.length}")
      totalDev.sum / totalDev.length
    }
  }
}
