package edu.berkeley.nlp.coref
import scala.collection.mutable.HashMap
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class OrderedClustering(val clusters: Seq[Seq[Int]]) {
  // Elements must be consecutive integers from 0 up to n
  private val allIndicesSorted = clusters.foldLeft(new ArrayBuffer[Int])(_ ++ _).sorted; 
  require(allIndicesSorted.sameElements((0 until allIndicesSorted.size).toSeq), allIndicesSorted);
  private val mentionToClusterMap = new HashMap[Int,Seq[Int]];
  for (cluster <- clusters) {
    for (i <- cluster) {
      mentionToClusterMap.put(i, cluster);
    }
  }
  
  def getCluster(idx: Int) = mentionToClusterMap(idx);
  
  def isSingleton(idx: Int) = mentionToClusterMap(idx).size == 1;
  
  def startsCluster(idx: Int) = mentionToClusterMap(idx)(0) == idx;
  
  def areInSameCluster(idx1: Int, idx2: Int) = {
    mentionToClusterMap.get(idx1) match {
      case None => idx1 == idx2
      case Some(cluster) => cluster.contains(idx2)
    }
  }
  
  def getImmediateAntecedent(idx: Int) = {
    val cluster = mentionToClusterMap(idx);
    val mentIdxInCluster = cluster.indexOf(idx);
    if (mentIdxInCluster == 0) {
      -1
    } else {
      cluster(mentIdxInCluster - 1);
    }
  }
  
  def getAllAntecedents(idx: Int) = {
    val cluster = mentionToClusterMap(idx);
    cluster.slice(0, cluster.indexOf(idx));
  }
  
  def getAllConsequents(idx: Int) = {
    val cluster = mentionToClusterMap(idx);
    cluster.slice(cluster.indexOf(idx) + 1, cluster.size);
  }
  
  
  // Needed for output printing
  def getClusterIdx(idx: Int) = {
    var clusterIdx = 0;
    for (i <- 0 until clusters.size) {
      if (clusters(i).sameElements(mentionToClusterMap(idx))) {
        clusterIdx = i;
      }
    }
    clusterIdx;
  }
  
  def getSubclustering(mentIdxsToKeep: Seq[Int]): OrderedClustering = {
    val oldIndicesToNewIndicesMap = new HashMap[Int,Int]();
    (0 until mentIdxsToKeep.size).map(i => oldIndicesToNewIndicesMap.put(mentIdxsToKeep(i), i));
    val filteredConvertedClusters = clusters.map(cluster => cluster.filter(mentIdxsToKeep.contains(_)).map(mentIdx => oldIndicesToNewIndicesMap(mentIdx)));
    val filteredConvertedClustersNoEmpties = filteredConvertedClusters.filter(cluster => !cluster.isEmpty); 
    new OrderedClustering(filteredConvertedClustersNoEmpties);
  }
}

object OrderedClustering {
  
  def createFromClusterIds(clusterIds: Seq[Int]) = {
    val mentIdAndClusterId = (0 until clusterIds.size).map(i => (i, clusterIds(i)));
    val clustersUnsorted = mentIdAndClusterId.groupBy(_._2).values;
    val finalClusters = clustersUnsorted.toSeq.sortBy(_.head).map(clusterWithClusterId => clusterWithClusterId.map(_._1));
    new OrderedClustering(finalClusters.toSeq);
  }
  
  def createFromBackpointers(backpointers: Seq[Int]) = {
    var nextClusterID = 0;
    val clusters = new ArrayBuffer[ArrayBuffer[Int]]();
    val mentionToCluster = new HashMap[Int,ArrayBuffer[Int]]();
    for (i <- 0 until backpointers.size) {
      if (backpointers(i) == i) {
        val cluster = ArrayBuffer(i);
        clusters += cluster;
        mentionToCluster.put(i, cluster); 
      } else {
        val cluster = mentionToCluster(backpointers(i));
        cluster += i;
        mentionToCluster.put(i, cluster);
      }
    }
    new OrderedClustering(clusters);
  }
}