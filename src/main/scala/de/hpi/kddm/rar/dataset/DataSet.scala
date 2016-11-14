/*
 * Copyright (c) 2016 Tom Bocklisch <https://github.com/tmbo>
 */
package de.hpi.kdd.rar.dataset

import com.typesafe.scalalogging.LazyLogging
import weka.core.Instances
import weka.filters.Filter
import weka.filters.unsupervised.attribute.Standardize

import scala.reflect.ClassTag
import scala.collection.JavaConversions._

/**
  * Interface to describe a dataset 
  */
trait DataSet extends SearchSpace {

  /**
    * Access the feature at the given index
    *
    * @param i index
    * @return feature at the index of the dataset
    */
  def featureAt(i: Int): Feature
}

/**
  * A dataset consisting of features and a label attribute 
  */
trait LabeledDataSet extends DataSet {

  /**
    * Labels of the instances
    *
    * @return labels
    */
  def labels: NominalFeature

  def name: String
}

object WekaDataSet extends LazyLogging{
  def standardize(data: Instances): Instances = {
    logger.info("Standardizing data!")
    val filter = new Standardize()
    filter.setInputFormat(data)
    Filter.useFilter(data, filter)
  }
}

/**
  * Representation of a UCI data set with data loaded into memory
  *
  * @param name      name of the data set
  * @param instances reference to the WEKA instances
  */
class WekaDataSet(
                  val name: String,
                  val instances: Instances,
                  val numClasses: Int
                )(implicit val tag: ClassTag[Feature]) extends LabeledDataSet {

  // access to the labels of the data set
  lazy val labels: NominalFeature = NominalFeature(
    instances.classAttribute(),
    instances)

  // access to the features of the data set
  lazy val features = instances.enumerateAttributes().map { attribute =>
    attribute.index -> Feature(attribute, instances)
  }.toMap

  def featureIndices = features.keys

  def isArtificial = false

  def featureAt(i: Int) =
    if (i == instances.classIndex)
      labels
    else
      features(i)

  def numberOfDimensions: Int = instances.numAttributes() - 1

  def stats = {
    s"Features: $numberOfDimensions " +
      s"Instances: ${instances.numInstances()} " +
      s"Nominal Attributes: ${features.count(_._2.attribute.isNominal)}"
  }
}