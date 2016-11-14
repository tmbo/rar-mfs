/*
 * Copyright (C) Tom Bocklisch <https://github.com/tmbo>
 */
package de.hpi.kddm.rar.hics

case class HicsContrastPramsFA(numIterations: Int, maxRetries: Double, alphaFixed: Double, maxInstances: Int) extends HiCSContrastParams {
  def alpha(numInstances: Int) = {
    if (alphaFixed * numInstances > maxInstances)
      maxInstances.toDouble / numInstances
    else
      alphaFixed
  }
}

case class HicsContrastPramsAA(numIterations: Int, maxRetries: Double, alphaGoal: Int) extends HiCSContrastParams {
  def alpha(numInstances: Int) =
    alphaGoal.toDouble / numInstances
}

trait HiCSContrastParams {
  def numIterations: Int

  def maxRetries: Double

  def alpha(numInstances: Int): Double
}
