# Relevance and Redundancy Framework (rar-mfs) [![Build Status](https://travis-ci.org/tmbo/rar-mfs.svg?branch=master)](https://travis-ci.org/tmbo/rar-mfs)
rar-mfs is an algorithm for feature selection and can be employed to select features from labled data sets. 

The Relevance and Redundancy Framework (RaR), which is the theory behind the implementation, is a novel feature selection algorithm that 
- works on large data sets (polynomial runtime),
- can handle differently typed features (e.g. nominal features and continoues features), and
- handles multivariate correlations.

## Installation
The tool is written in scala and uses the weka framework to load and handle data sets. You can either run it independently providing the data as an `.arff` or `.csv` file or you can include the algorithm as a (maven / ivy) dependency in your project. As an example data set we use [heart-c](http://www.cs.umb.edu/~rickb/files/UCI/heart-c.arff). 

### Project dependency
The project is published to maven central ([link](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22rar-mfs%22)). To depend on the project use:
- maven
    ```xml
    <dependency>
      <groupId>de.hpi.kddm</groupId>
      <artifactId>rar-mfs_2.11</artifactId>
      <version>1.0.2</version>
    </dependency>
    ```
    
- sbt:
    ```sbt
    libraryDependencies += "de.hpi.kddm" %% "rar-mfs" % "1.0.2"
    ```
  
To run the algorithm use
```scala
import de.hpi.kddm.rar._

// ...
val dataSet = de.hpi.kddm.rar.Runner.loadCSVDataSet(new File("heart-c.csv", isNormalized = false, "")
val algorithm = new RaRSearch(
      HicsContrastPramsFA(numIterations = config.samples, maxRetries = 1, alphaFixed = config.alpha, maxInstances = 1000),
      RaRParamsFixed(k = 5, numberOfMonteCarlosFixed = 5000, parallelismFactor = 4))

algorithm.selectFeatures(dataSet)
```

### Command line tool
- EITHER download the prebuild binary which requires only an installation of a recent java version (>= 6)
    1. download the prebuild jar from the releases tab ([latest](https://github.com/tmbo/rar-mfs/releases/download/v1.0.2/rar-mfs-1.0.2.jar))
    2. run `java -jar rar-mfs-1.0.2.jar--help`

    Using the prebuild jar, here is an example usage:
    ```sh
    rar-mfs > java -jar rar-mfs-1.0.2.jar arff --samples 100 --subsetSize 5 --nonorm heart-c.arff
    Feature Ranking:
      1 - age (12)
      2 - sex (8)
      3 - cp (11)
      ...
    ```
- OR build the repository on your own: 
    1. make sure [sbt](http://www.scala-sbt.org/) is installed 
    2. clone repository 
    3. run `sbt run`
    
   Simple example using sbt directly after cloning the repository:
    ```sh
    rar-mfs > sbt "run arff --samples 100 --subsetSize 5 --nonorm heart-c.arff"
    Feature Ranking:
      1 - age (12)
      2 - sex (8)
      3 - cp (11)
      ...
    ``` 
    
### [Optional]
To speed up the algorithm, consider using a fast solver such as Gurobi (http://www.gurobi.com/). Install the solver and put the provided `gurobi.jar` into the java classpath. 

## Algorithm
### Idea
Abstract overview of the different steps of the proposed feature selection algorithm:

![Algorithm Overview](https://github.com/tmbo/rar-mfs/blob/master/docu/images/algorithm_overview.png)

The Relevance and Redundancy ranking framework (RaR) is a method able to handle large scale data sets and data sets with mixed features. Instead of directly selecting a subset, a feature ranking gives a more detailed overview into the relevance of the features. 

The method consists of a multistep approach where we 
  1. repeatedly sample subsets from the whole feature space and examine their relevance and redundancy: exploration of the search space to gather more and more knowledge about the relevance and redundancy of features

  2. decude scores for features based on the scores of the subsets
  
  3. create the best possible ranking given the sampled insights.

### Parameters
| Parameter  | Default value | Description |
| ---------- | ------------- | ------------|
| *m* - contrast iterations      | 100     | Number of different slices to evaluate while comparing marginal and conditional probabilities |
| *alpha* - subspace slice size | 0.01    | Percentage of all instances to use as part of a slice which is used to compare distributions |
| *n* - sampling itertations     | 1000    | Number of different subsets to select in the sampling phase|
| *k* - sample set size          | 5       | Maximum size of the subsets to be selected in the sampling phase|
## License
Current license is CC BY-NC 4.0. The complete license can be found at [creativecommons](http://creativecommons.org/licenses/by-nc/4.0/legalcode). For commercial licensing please contact the author.

<p>
  <img src="https://hpi.de/fileadmin/user_upload/fachgebiete/mueller/images/hpi_logo_fb_KnowlDiscoveryDataMining_web80.png"/>
</p>
