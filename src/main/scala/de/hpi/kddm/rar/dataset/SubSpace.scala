/*
 * Copyright (c) 2016 Tom Bocklisch <https://github.com/tmbo>
 */
package de.hpi.kddm.rar.dataset

import weka.core.Instances

import scala.collection.breakOut
import scala.collection.immutable.BitSet

/**
  * A space to search
  */
trait SearchSpace {
  def instances: Instances

  def featureAt(i: Int): Feature

  def featureIndices: Iterable[Int]

  def labels: NominalFeature

  def numberOfDimensions: Int

  def numClasses: Int

  def firstDimension = 0

  def isArtificial: Boolean

  def tailDimensions = 1 until numberOfDimensions

  def createSubSpace(dimensions: Int*) =
    SubSpaceWithLabel(restrictedTo = BitSet(dimensions: _*), space = this, labels)

  def asSubSpace =
    SubSpaceWithLabel(restrictedTo = BitSet((0 until numberOfDimensions): _*), space = this, labels)
}

/**
  * Part of a search space
  */
trait SubSpace{
  def dimensions: BitSet

  def labels: NominalFeature

  def features: Map[Int, Feature]

  def allDimensions: Map[Int, Feature]

  def featureAt(i: Int): Feature

  def include(dimension: Int): SubSpace

  def exclude(dimension: Int): SubSpace

  def numberOfDimensions: Int

  def numberOfInstances: Int

  def space: SearchSpace
}

/**
  * A subspace that contains labels for the instances
  */
case class SubSpaceWithLabel(restrictedTo: BitSet, space: SearchSpace, labels: NominalFeature) extends SubSpace {
  def dimensions =
    restrictedTo

  lazy val features: Map[Int, Feature] =
    restrictedTo.map(d => d -> space.featureAt(d))(breakOut)

  lazy val allDimensions: Map[Int, Feature] = features.updated(space.instances.classIndex, labels)

  def include(dimension: Int) =
    copy(restrictedTo + dimension, space)

  def featureAt(i: Int) =
    space.featureAt(i)

  def exclude(dimension: Int) =
    copy(restrictedTo - dimension, space)

  def numberOfDimensions =
    restrictedTo.size

  def numberOfInstances =
    space.instances.numInstances()
}