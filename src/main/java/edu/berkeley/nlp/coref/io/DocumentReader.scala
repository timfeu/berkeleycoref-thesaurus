package edu.berkeley.nlp.coref.io

import java.io._

import edu.berkeley.nlp.coref.BaseDoc
import edu.berkeley.nlp.coref.io.impl.ConllDocReader
import edu.berkeley.nlp.futile.util.Logger

import scala.collection.mutable.ArrayBuffer

/**
 * Reads coreference documents from text files in a format specified by the extending class.
 */
trait DocumentReader {

  /**
   * Loads documents from a single text file.
   *
   * @param file the text file
   *
   * @return All documents contained in the text file
   */
  def loadDocs(file: File): Seq[BaseDoc] = loadDocs(new FileReader(file))

  /**
   * Loads document from a reader.
   *
   * @param reader a reader on the document
   *
   * @return All documents that were readable
   */
  def loadDocs(reader: Reader): Seq[BaseDoc]

  /**
   * Loads documents from a single text file in the format specified by the implementing class.
   *
   * @param filename name of the text file
   *
   * @return All documents contained in the text file
   */
  def loadDocs(filename: String): Seq[BaseDoc] = loadDocs(new File(filename))



  /**
   * Reads up to numDocs documents from the specified folder. The folder is not traversed recursively.
   *
   * @param folder the folder to read the documents from
   * @param filenameFilter filter applied to all filenames in the directory
   * @param numDocs number of documents to read
   *
   * @return documents from the folder up to a number of numDocs
   */
  def loadDocsFromFolder(folder: File, filenameFilter: Option[FilenameFilter], numDocs: Option[Int] = None): Seq[BaseDoc] = {
    if (numDocs.isDefined && numDocs.get < 0) {
      throw new IllegalArgumentException("numDocs must be non-negative")
    }

    if (numDocs.isDefined) Logger.logss("Loading " + numDocs.get + " docs from " + folder) else Logger.logss("Loading all docs from " + folder)

    var out = new ArrayBuffer[BaseDoc]()

    var docsProcessed = 0
    var fileIdx = 0

    val files = if (filenameFilter.isDefined) folder.listFiles(filenameFilter.get) else folder.listFiles()

    if (files == null) {
      throw new IOException("The folder " + folder + " cannot be opened")
    }

    while ((numDocs.isEmpty || docsProcessed < numDocs.get) && fileIdx < files.size) {
      val docs = loadDocs(files(fileIdx))
      docsProcessed += docs.size
      out ++= docs
      fileIdx += 1
    }

    if (docsProcessed == 0) Logger.logss(s"WARNING: Zero docs loaded from $folder ... double check your paths unless you meant for this happen")

    out = if (numDocs.isDefined) out.take(numDocs.get) else out

    Logger.logs("Loaded " + out.size + " docs")

    out.toSeq
  }

  /**
   * Reads up to numDocs documents from the specified folder. The folder is not traversed recursively. If numDocs
   * is -1, all documents will be loaded.
   *
   * This method is convenient for being called from Java source code as it does not reference Scala Options.
   *
   * @param folder the folder to read the documents from
   * @param filenameFilter filter applied to all filenames in the directory or null if no filter should be applied
   * @param numDocs number of documents to read or -1 if all documents should be read
   *
   * @return documents from the folder up to a number of numDocs
   */
  def loadDocsFromFolderSimple(folder: File, filenameFilter: FilenameFilter, numDocs: Int): Seq[BaseDoc] = {
    require(folder != null)
    val numDocsOption = if (numDocs == -1) None else Some(numDocs)
    val filenameFilterOption = if (filenameFilter == null) None else Some(filenameFilter)
    loadDocsFromFolder(folder, filenameFilterOption, numDocsOption)
  }
}
