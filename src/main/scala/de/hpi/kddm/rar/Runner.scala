package de.hpi.kddm.rar

import java.io.{BufferedReader, File, FileReader}
import java.nio.file.Path

import com.typesafe.scalalogging.LazyLogging
import de.hpi.kdd.rar.RaRSearch
import de.hpi.kdd.rar.RaRSearch.RaRParamsFixed
import de.hpi.kdd.rar.dataset.WekaDataSet
import de.hpi.kdd.rar.hics.{HicsContrastPramsAA, HicsContrastPramsFA}
import weka.core.Instances
import weka.core.converters.CSVLoader
import weka.filters.Filter
import weka.filters.unsupervised.attribute.Remove

import scala.reflect.ClassTag

sealed trait DataSetType
case class ArffDataSet() extends DataSetType
case class CSVDataSet(hasHeader: Boolean = true, nominalFeatures: Seq[Int] = Seq.empty) extends DataSetType

case class Config(
                   dataset: File = new File("example-dataset/heart-c.arff"),
                   mode: Option[DataSetType] = None,
                   samples: Int = 1000,
                   subsetSize: Int = 5,
                   alpha: Double = 0.01,
                   shouldNormalize: Boolean = true,
                   contrastSamples: Int = 100) {

  def modifyMode[A <: DataSetType](f: A => A)(implicit c: ClassTag[A]) = {
    copy(mode = mode.map(m => f(m.asInstanceOf[A])))
  }
}


object Runner extends LazyLogging{
  /**
    * Load a dataset from a CSV file. Since, a CSV doesn't contain all the necessary information an ARFF file contains
    * a couple of parameters need to be specified to make up for that gap.
    *
    * @param file              CSV file to be loaded
    * @param nominalAttributes string list of indices for nominal attributes, e.g. "1, 2, 4-7, last",
    *                          WARN: first feature has index 1 not 0! i.e. the last feature has index num(features)
    * @param idxOfClassAttr    idx of the class attribute, negative to count from the back. Default is `-1`, i.e. the last
    *                          attribute
    *                          WARN: first feature has index 0 not 1! i.e. the last feature has index num(features)-1
    * @param hasHeader         defines if the CSV contains a row containing feature names
    * @return loaded data set
    */
  private def loadCSVDataSet(file: File, isNormalized: Boolean, nominalAttributes: String, ignoredAttributes: String = "", idxOfClassAttr: Int = -1, hasHeader: Boolean = true) = {
    // load CSV and set all nominal features
    val loader = new CSVLoader()
    loader.setSource(file)
    loader.setNoHeaderRowPresent(!hasHeader)
    loader.setNominalAttributes(nominalAttributes)

    var data = loader.getDataSet

    // Remove invalid features
    val remove = new Remove()
    remove.setAttributeIndices(ignoredAttributes)
    remove.setInputFormat(data)
    data = Filter.useFilter(data, remove)

    if (idxOfClassAttr < 0)
      data.setClassIndex(data.numAttributes() + idxOfClassAttr)
    else
      data.setClassIndex(idxOfClassAttr)
    val instances = if(isNormalized) data else WekaDataSet.standardize(data)

    logger.info(s"Loaded dataset '${file.getName}'. Summary: " + data.toSummaryString)

    new WekaDataSet(file.getName, instances, instances.numClasses)
  }

  /**
    * Read Arff dataset from disk
    */
  def loadArffDataSet(file: File, isNormalized: Boolean, numClasses: Option[Int] = None) = {
    logger.info("Loading data set from: " + file.getAbsolutePath)
    val name = file.getName.split(".").headOption.getOrElse("unnamed")
    val reader = new BufferedReader(new FileReader(file))
    val data = new Instances(reader)
    data.setClassIndex(data.numAttributes() - 1)
    val instances = if(isNormalized) data else WekaDataSet.standardize(data)

    logger.info(s"Loaded dataset '$name'. Summary: " + data.toSummaryString)

    new WekaDataSet(name, instances, numClasses getOrElse instances.numClasses())
  }

  private def runAlgorithm(config: Config) = {
    val algorithm = new RaRSearch(
      HicsContrastPramsFA(numIterations = config.samples, maxRetries = 1, alphaFixed=config.alpha, maxInstances = 1000),
      RaRParamsFixed(k = 5, 5000, parallelismFactor = 4))

    val dataSet = config.mode.get match {
      case arffConf: ArffDataSet =>
        loadArffDataSet(
          config.dataset,
          isNormalized = !config.shouldNormalize)
      case csvConf: CSVDataSet =>
        loadCSVDataSet(
          config.dataset,
          isNormalized = config.shouldNormalize,
          nominalAttributes = csvConf.nominalFeatures.map(_ + 1).mkString(","),
          ignoredAttributes = "",
          idxOfClassAttr = -1,
          hasHeader = csvConf.hasHeader)
    }
    val results = algorithm.selectFeatures(dataSet)
    logger.info("Feature Ranking:")
    results.zipWithIndex.foreach{
      case (f, idx) =>
        logger.info(s"\t${idx+1} - ${dataSet.instances.attribute(idx).name} ($f)")
    }
  }

  def main(args: Array[String]) {
    val parser = new scopt.OptionParser[Config]("rar-mfs") {
      head("rar-mfs", "1.0")

      opt[Double]('a', "alpha").action((x, c) =>
        c.copy(alpha = x)).text("subspace slice size")

      opt[Int]('n', "samples").action((x, c) =>
        c.copy(samples = x)).text("sampling itertations")

      opt[Int]('m', "contrastSamples").action((x, c) =>
        c.copy(contrastSamples = x)).text("contrast iterations")

      opt[Int]('k', "subsetSize").action((x, c) =>
        c.copy(subsetSize = x)).text("sample set size")

      opt[Unit]("nonorm").action( (x, c) =>
        c.copy(shouldNormalize = false)).text("flag to indicate that no normalization of the data set is necessary")

      arg[File]("<file>...").action((x, c) =>
        c.copy(dataset = x)).text("data set containing instances (csv or arff)")

      help("help").text("prints this usage text")

      cmd("arff").
        action( (_, c) => c.copy(mode = Some(ArffDataSet())) ).
        text("arff data set analysis")

      cmd("csv").
        action( (x, c) => c.copy(mode = Some(CSVDataSet())) ).
        text("csv data set analysis").
        children(
          opt[Boolean]('h', "header").action( (x, c) =>
            c.modifyMode[CSVDataSet](_.copy(hasHeader = x)) ).text("defines if the csv has a header line"),
          opt[Seq[Int]]('z', "nominals").valueName("<nom1>,<nom2>...").action( (x,c) =>
            c.modifyMode[CSVDataSet](_.copy(nominalFeatures = x))).text("xyz is a boolean property")
        )

      checkConfig {
        case c: Config if c.mode.isEmpty => failure("No command specified (e.g. 'arff')")
        case _ => success
      }
    }

    // parser.parse returns Option[C]
    parser.parse(args, Config()) match {
      case Some(config) =>
        runAlgorithm(config)
      case None =>

    }
  }
}
