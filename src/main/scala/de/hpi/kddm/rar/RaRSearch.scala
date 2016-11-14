/*
 * Copyright (C) Tom Bocklisch <https://github.com/tmbo>
 */
package de.hpi.kddm.rar

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.scalalogging.LazyLogging

import de.hpi.kddm.rar.RaRSearch._
import de.hpi.kddm.rar.util.ImplicitExtensions._
import de.hpi.kddm.rar.dataset.{ContinuousFeature, NominalFeature, SearchSpace, SubSpace}
import de.hpi.kddm.rar.hics.stattest.{KLTest, KSTest}
import de.hpi.kddm.rar.hics.{HiCSContrastMeasure, HiCSContrastParams}

import scala.collection.immutable.BitSet
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object RaRSearch {

  /**
    * Parameters that control the inner workings of the tabular contrast algorithm
    */
  trait RaRParams {
    /**
      * Maximum size of the subset to select in each random trial. This inherently defines
      * the maximal size of the cluster that can be detected. Because the algorithm always
      * only evaluates a subset of the features, it will never find correlated clusters of a
      * size bigger than k. Nevertheless, setting k to high leads to a low specificity of the
      * contrast measure.
      */
    def k: Int

    /**
      * Defines how many operations are allowed to be performed in parallel
      */
    def parallelismFactor: Int

    /**
      * Defines how many random trials are used to fill the tables. The more tries we allow
      * the more accurate the results will be and the smaller correlations we will find. Can
      * be static or dependent on the number of features.
      *
      * @param numFeatures number of features in the dataset
      * @return
      */
    def numberOfMonteCarlos(numFeatures: Int): Int
  }

  /**
    * Use a static set of parameters
    */
  case class RaRParamsFixed(k: Int, numberOfMonteCarlosFixed: Int, parallelismFactor: Int = 1) extends RaRParams {
    def numberOfMonteCarlos(numFeatures: Int): Int =
      numberOfMonteCarlosFixed
  }

  /**
    * Calculate the parameters, esp. number of random tries according to given beta and m.
    *
    * alpha defines the probability of missing a cluster of size m during the evaluation.
    */
  case class RaRParamsAdaptive(k: Int, m: Int, beta: Double, min: Int, parallelismFactor: Int = 1) extends RaRParams with LazyLogging {
    def numberOfMonteCarlos(numFeatures: Int): Int = {
      val n = math.ceil(math.log(beta) /
        math.log(1 - ((numFeatures - m) choose (k - m)).toDouble / (numFeatures choose k))).toInt
      logger.info(s"Calculated number of tries $n (min $min)")
      math.max(n, min)
    }
  }

  case class RaRParamsLinear(k: Int, iterationsPerFeature: Int, min: Int, parallelismFactor: Int = 1) extends RaRParams with LazyLogging {
    def numberOfMonteCarlos(numFeatures: Int): Int = {
      math.max(min, numFeatures * iterationsPerFeature)
    }
  }

  case class ContrastScores(withSubset: Array[RedundancyScore], withLabel: Array[RelevanceScore], subspace: Array[Int])

  case class RelevanceScore(score: Double, subset: BitSet)

  case class RedundancyScore(score: Double, f: Int, subset: BitSet)
}

/**
  * A tubular algorithm that uses a table of cells to keep track of estimated relevancy and redundancy scores.
  *
  * The table is quadratic with one row & column for each feature. Initialy all entries are set to zero / are empty.
  * During the search the cells are filled with estimations based on random tries.
  *
  * @param hicsParams Parameters to be used for the underlying contrast algorith,
  * @param prcParams  Parameters of the tabular contrast
  */
class RaRSearch(val hicsParams: HiCSContrastParams, val prcParams: RaRParams)
    extends RelevanceReasoner
    with LazyLogging {

  implicit val executorService = Executors.newFixedThreadPool(math.max(1, prcParams.parallelismFactor))

  // Keep a fixed seed to get reproducable results
  val rand = new Random(seed = 1337)

  // A context to keep around between evaluations
  case class ContrastContext(statTest: KSTest)

  private def expectedMParameter(subSpace: SubSpace) = {
    math.max((hicsParams.alpha(subSpace.numberOfInstances) * subSpace.numberOfInstances).toInt, 5)
  }

  def initContext(searchSpace: SearchSpace): ContrastContext = {
    val subSpace = searchSpace.asSubSpace
    logger.info("Finished sorting the data")
    val expectedM = expectedMParameter(subSpace)
    val statTest = KSTest.init(subSpace, expectedM, calibrationIterations = 100)
    logger.info("Finished initializing KS-Test")
    ContrastContext(statTest)
  }

  /**
    * Given a feature search space, rank the features according to their decreasing usefullness
    * @param searchSpace search space
    * @return ranked features
    */
  def selectFeatures(searchSpace: SearchSpace): List[Int] = {
    val N = searchSpace.numberOfDimensions

    val availableDimensions = searchSpace.featureIndices.toSeq

    val context = initContext(searchSpace)

    val numberOfTries = prcParams.numberOfMonteCarlos(N)

    val progressCounter = new AtomicInteger()

    // We are going to update our tables using the above defined number of tries
    val sampledScores = (0 until numberOfTries)
      .toArray
      .parWithMaxParallelism(prcParams.parallelismFactor)
      .map { iteration =>
        // Dice the size of the subset to be selected
        val k = rand.nextInt(prcParams.k) + 2     // prcParams.k + 1
        // select a random subset from our available dimensions
        val subspace = selectRandomSubspace(availableDimensions, k)

        val s = score(subspace, searchSpace, context)
        // Log progress
        val i = progressCounter.getAndIncrement()
        if (i % 10 == 0)
          logger.debug(s"Finished ${(i.toDouble / numberOfTries * 100).toInt}%")
        s
      }

    // Instantiate our tables that we are going to fill
    val interC = ArrayBuffer.empty[RedundancyScore]
    // Buffer that stores subsets and their calculated contrast with the label
    val labelC = ArrayBuffer.empty[RelevanceScore]

    // Iterate over the scores to create intermediate structures needed to calculate the final ranking
    timeMeasured("Calculation of intermediate structures")(sampledScores.seq.foreach { scored =>
      // Keep track of the subset and its relevance score
      labelC.appendAll(scored.withLabel)
      // Store redundancy values, f and S_i
      interC.appendAll(scored.withSubset)
    })

    calculateRanking(availableDimensions, labelC, interC)
  }

  /**
    * Given a number of dimensions select a random subset of size k.
    *
    * @param availableDimensions dimensions
    * @param k                   size of random subset
    * @return random subset
    */
  private def selectRandomSubspace(availableDimensions: Seq[Int], k: Int) = {
    rand.shuffle(availableDimensions).take(k).toArray
  }

  /**
    * Given a set of sampled redundancy values calculate the maximal justified redundancy of a
    * feature f given a set T
    *
    * @param f      feature
    * @param interC sampled redundancy scores
    * @param T      set
    * @return maximal redundancy of f wrt T
    */
  private def calculateRedundancy(f: Int, T: Set[Int], interC: ArrayBuffer[RedundancyScore]) = {
    if (T.isEmpty)
      0.0
    else {
      val sorted = interC.filter(_.f == f).sortBy(_.score).toList
      def isJustified(score: RedundancyScore, others: List[RedundancyScore]) = {
        val elementsInSet = score.subset.intersect(T)
        val size = elementsInSet.size
        !others.exists(_.subset.intersect(elementsInSet).size == size)
      }

      def selectFirstJustified(scores: List[RedundancyScore]): Double = {
        scores match {
          case head :: tail =>
            if (isJustified(head, tail))
              head.score
            else
              selectFirstJustified(tail)
          case Nil =>
            0.0
        }
      }

      selectFirstJustified(sorted)
    }
  }

  /**
    * Rank teh features using our filled relevancy and redundancy tables
    *
    * @param dimensions dimensions to rank
    * @param labelCs    label contrast table
    * @param interC     subset redundancy table
    * @return ranked features
    */
  private def calculateRanking(
                                dimensions: Seq[Int],
                                labelCs: ArrayBuffer[RelevanceScore],
                                interC: ArrayBuffer[RedundancyScore]) = {
    val cys = calculateSingleFeatureRelevance(labelCs, dimensions)

    def rankR(previous: Set[Int], available: Set[Int]): List[Int] = {
      if (available.nonEmpty) {
        val scored = available.map { i =>
          val relevancy = cys(i)
          // calculate redundancy with the features we have already selected
          val redundancy = calculateRedundancy(i, previous, interC)
          // rank features using f-score on (1-redundancy) and relevancy
          i -> (2 * (1 - redundancy) * relevancy / ((1 - redundancy) + relevancy))
        }

        val nextBest = scored.maxBy(_._2)._1
        nextBest :: rankR(previous + nextBest, available - nextBest)
      } else
        Nil
    }

    rankR(Set.empty, dimensions.toSet)
  }

  /**
    * Caluclate the score of the passed subset of dimensions.
    *
    * @param dimensions  dimensions to evaluate
    * @param searchSpace search space
    * @param context     context to be used for the calculation
    * @return scores
    */
  private def score(dimensions: Array[Int], searchSpace: SearchSpace, context: ContrastContext): ContrastScores = {
    logger.trace("\tCalculating contrast with LABEl")

    val subspace = searchSpace.createSubSpace(dimensions: _*)

    // Evaluate the contrast of the subset without the passed dimension with respect to the label. This is an indicator
    // of how important the dimension is for the prediction (e.g. if the score drops compared to the subset including
    // `dimension`, the feature seems to be helpful for the prediction task)
    val contrasts = dimensions.parWithMaxParallelism(prcParams.parallelismFactor).map { d =>
      val contrast = HiCSContrastMeasure.calculateContrast(
        subspace.exclude(d),
        searchSpace.labels,
        hicsParams,
        KLTest,
        shouldNormalize = false)
      RelevanceScore(contrast, BitSet(dimensions.filter(_ != d): _*))
    }.seq.toArray

    // Score the dimension using the contrast between the dimension and all the other features of the subset. This is
    // an indicator of how much redundant information the dimension contains
    logger.trace("\tCalculating contrast with SUBSETS")
    val contrastWithSubset =
      dimensions.parWithMaxParallelism(prcParams.parallelismFactor).map { d =>
        // depending on the feature type, we will use different statistical tests suitable for the feature type
        val contrast = searchSpace.featureAt(d) match {
          case f: NominalFeature =>
            HiCSContrastMeasure.calculateContrast(
              subspace.exclude(d),
              f,
              hicsParams,
              KLTest,
              shouldNormalize = true)
          case f: ContinuousFeature =>
            HiCSContrastMeasure.calculateContrast(
              subspace.exclude(d),
              f,
              hicsParams,
              context.statTest,
              shouldNormalize = true)
        }

        RedundancyScore(contrast, d, BitSet(dimensions.filter(_ != d): _*))
      }.seq.toArray

    ContrastScores(contrastWithSubset, contrasts, dimensions)
  }
}