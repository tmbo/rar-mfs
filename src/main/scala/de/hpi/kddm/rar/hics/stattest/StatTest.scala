/*
 * Copyright (c) 2016 Tom Bocklisch <https://github.com/tmbo>
 */

package de.hpi.kddm.rar.hics.stattest

import de.hpi.kddm.rar.dataset.{Feature, SubSpace}
import de.hpi.kddm.rar.hics.IndexSelection

/**
  * Functionality to compute the dependence of cmpAttr with respect to the subSpace using the instances selected in a
  * index selection.
  */
trait StatTest[F <: Feature]{
  def computeDeviation(
                        subSpace: SubSpace,
                        cmpAttr: F,
                        selection: IndexSelection,
                        shouldNormalize: Boolean): Double

  /**
    * Transform the value to [0, 1] interval
    */
  protected def normalize(value: Double) =
    1.0 - math.pow(math.E, -value)
}
