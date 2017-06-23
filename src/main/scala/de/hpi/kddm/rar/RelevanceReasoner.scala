package de.hpi.kddm.rar

import com.typesafe.scalalogging.LazyLogging
import de.hpi.kddm.rar.RaRSearch.RelevanceScore
import de.hpi.kddm.rar.util.TimeMeasurement

trait RelevanceReasoner extends LazyLogging with TimeMeasurement {

  import optimus.algebra.Expression
  import optimus.optimization._

  // Decide which underlying QP solver to use. gurobi is way faster but needs to be installed on the
  // system separately. Fallback to java implementation if faster one is not present.
  lazy val solver = {
    val useGurobi = try {
      solvers.contains(SolverLib.gurobi)
    } catch {
      case _: java.lang.UnsatisfiedLinkError | _: Exception | _: java.lang.NoClassDefFoundError =>
        false
    }

    if (useGurobi)
      SolverLib.gurobi
    else {
      logger.warn("Using JAVA QL Optimizer, which might be slow. Try installing gurobi!")
      SolverLib.ojalgo
    }
  }

  /**
    * Defines a distance function with respect to s. For now lets use squared distance between
    * a variable and a mean
    *
    * @param els  elements
    * @param mean mean
    * @return expression for summation of squared distances between elements and mean
    */
  private def squaredDistance(els: Iterable[MPFloatVar], mean: MPFloatVar) = {
    els.map(v => (v - mean) * (v - mean)).reduce[Expression](_ + _)
  }

  /**
    * Given our scored subsets try to reason about single relevancy scores for the features. We need to do this, since
    * the scores we calculated are based on a subset of features. So we don't know which feature has which influence on
    * the score. But since we do have quite a number of different subsets and their scores we can combine that
    * knowledge to deduce which features might have which influence
    *
    * @param relevancyScorings subsets and their relevancy scores
    * @param dimensions        dimensions to analyze
    * @return map of dimensions and their relevance
    */
  def calculateSingleFeatureRelevance(relevancyScorings: Seq[RelevanceScore], dimensions: Seq[Int]) = {
    // We are going to treat this as a quadratic programming problem.
    implicit val problem = LQProblem(solver)

    val N = dimensions.length

    val maxScore = relevancyScorings.maxBy(_.score).score

    val solverVariables = dimensions.map { i =>
      i -> MPFloatVar("x" + i, lBound = 0, uBound = maxScore)
    }.toMap

    val s = MPFloatVar("s")

    val allVarsSum = solverVariables.values.reduce[Expression](_ + _)

    add(s := (allVarsSum * (1.0 / N)))

    minimize(allVarsSum + squaredDistance(solverVariables.values, s))

    relevancyScorings.zipWithIndex.foreach {
      case (relevanceScore, idx) =>
        val objectiveVars = relevanceScore.subset.map(i => solverVariables(i))
        val objectiveSum = objectiveVars.reduce[Expression](_ + _)
        add(objectiveSum >:= relevanceScore.score)
    }

    val (succeeded, time) = timeIt(start())
    logger.info(f"QPO Runtime ${time.toDouble / 1000}%.2fs. Minimized objective: ${problem.objectiveValue}%.2f")
    release()
    if (succeeded) {
      val varMax = solverVariables.values.map(_.value.get).max
      solverVariables.mapValues(_.value.get / varMax)
    } else {
      throw new Exception("Failed to optimize QP.")
    }
  }
}
