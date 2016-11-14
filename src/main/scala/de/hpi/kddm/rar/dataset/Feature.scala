/*
 * Copyright (c) 2016 Tom Bocklisch <https://github.com/tmbo>
 */

package de.hpi.kdd.rar.dataset

import java.util

import com.typesafe.scalalogging.LazyLogging
import de.hpi.kdd.rar.util.Statistics
import weka.core.{Attribute, Instances}

trait Feature {
  def attribute: Attribute

  def underlying: Instances

  override def toString = {
    s"F(${attribute.index()})"
  }
}

object Feature{
  def apply(attribute: Attribute, underlying: Instances) = {
    if(attribute.isNominal)
      NominalFeature(attribute, underlying)
    else
      ContinuousFeature(attribute, underlying)
  }
}


/**
  * A feature wrapper around a WEKA attribute providing helper methods
  *
  * @param attribute  attribute / feature
  * @param underlying WEKA instances
  */
case class ContinuousFeature(attribute: Attribute, underlying: Instances) extends Feature with LazyLogging{

  // Attributes values converted into a double array
  lazy val values = underlying.attributeToDoubleArray(attribute.index)

  // Mean / Median calculation
  lazy val meanOrMedian: Double = breeze.stats.mean(values)

  // Sorted representation of the underlying feature value indices
  lazy val sortedValues = values.zipWithIndex.sortBy(_._1).map(_._2)

  // Sorted representation of the underlying feature values
  lazy val sortedIndices = {
    val result = new Array[Int](values.length)
    var i = 0
    while(i < values.length){
      result(sortedValues(i)) = i
      i += 1
    }
    result
  }

}

/**
  * A feature wrapper around a WEKA attribute providing helper methods
  *
  * @param attribute  attribute / feature
  * @param underlying WEKA instances
  */
case class NominalFeature(attribute: Attribute, underlying: Instances) extends Feature with LazyLogging{

  // Attributes values converted into a double array
  lazy val values = underlying.attributeToDoubleArray(attribute.index)

  // Mean / Median calculation
  lazy val meanOrMedian: Double = breeze.stats.mode(values).mode

  // Distribution of the values. The map represents a mapping
  // from values to frequencies of that value in this feature
  lazy val valueDist: Array[(Double, Int)] = Statistics.valueDistribution(values, attribute.numValues()).toArray

  // An indicator map that for each value of the feature stores at which index that value occurs
  lazy val valueIndex: Map[Double, util.BitSet] = {
    var indicatorMap = Map.empty[Double, util.BitSet]

    def bitMapFor(value: Double) = {
      indicatorMap.getOrElse(value, {
        val bm = new util.BitSet
        indicatorMap = indicatorMap.updated(value, bm)
        bm
      })
    }

    var i = 0
    while (i < values.length) {
      bitMapFor(values(i)).set(i)
      i += 1
    }
    indicatorMap
  }
}