package classifiers

import org.apache.spark.ml.{Pipeline => SparkMlPipeline}
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.feature.{Word2Vec, StringIndexer, Tokenizer}
import org.apache.spark.mllib.classification.LogisticRegressionWithSGD
import twitter.LinguisticTransformer

object Pipeline {

  val linguisticTransformer = new LinguisticTransformer().setInputCol("tweet").setOutputCol("normTweet")

  val tokenizer = new Tokenizer().setInputCol("normTweet").setOutputCol("tokens")

  val indexer = new StringIndexer().setInputCol("sentiment").setOutputCol("label")

  /**
   * Params and defaults:
   *
   * maxIter: maximum number of iterations (>= 0) (default: 1)
   * minCount: the minimum number of times a token must appear to be included in the word2vec model's vocabulary (default: 5)
   * numPartitions: number of partitions for sentences of words (default: 1)
   * outputCol: output column name (default: w2v_2521010012f2__output, current: features)
   * seed: random seed (default: -1961189076)
   * stepSize: Step size to be used for each iteration of optimization. (default: 0.025)
   * vectorSize: the dimension of codes after transforming from words (default: 100)
   */
  val word2Vec = new Word2Vec().setInputCol("tokens").setOutputCol("features")

  /**
   * Params and defaults:
   *
   * featuresCol: features column name (default: features)
   * fitIntercept: whether to fit an intercept term (default: true)
   * labelCol: label column name (default: label)
   * maxIter: maximum number of iterations (>= 0) (default: 100)
   * predictionCol: prediction column name (default: prediction)
   * probabilityCol: Column name for predicted class conditional probabilities. Note: Not all models output well-calibrated probability estimates! These probabilities should be treated as confidences, not precise probabilities. (default: probability)
   * rawPredictionCol: raw prediction (a.k.a. confidence) column name (default: rawPrediction)
   * regParam: regularization parameter (>= 0) (default: 0.0)
   * standardization: whether to standardize the training features before fitting the model. (default: true)
   * threshold: threshold in binary classification prediction, in range [0, 1] (default: 0.5)
   * thresholds: Thresholds in multi-class classification to adjust the probability of predicting each class. Array must have length equal to the number of classes, with values >= 0. The class with largest value p/t is predicted, where p is the original probability of that class and t is the class' threshold. (undefined)
   * tol: the convergence tolerance for iterative algorithms (default: 1.0E-6)
   */
  val estimator = new LogisticRegression()

  def create: SparkMlPipeline = new SparkMlPipeline().setStages(Array(indexer, linguisticTransformer, tokenizer, word2Vec, estimator))

}
