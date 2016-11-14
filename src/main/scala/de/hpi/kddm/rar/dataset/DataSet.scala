/*
 * Copyright (c) 2016 Tom Bocklisch <https://github.com/tmbo>
 */
package de.hpi.kdd.rar.dataset

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