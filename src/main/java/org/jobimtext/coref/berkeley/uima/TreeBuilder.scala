package org.jobimtext.coref.berkeley.uima

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.`type`.pos.POS
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.`type`.Token
import de.tudarmstadt.ukp.dkpro.core.api.syntax.`type`.constituent.Constituent
import edu.berkeley.nlp.futile.syntax.Tree
import org.apache.uima.fit.util.JCasUtil
import org.apache.uima.jcas.tcas.Annotation

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

/**
 * Converts a berkeley tree from constituents in a CAS.
 *
 * @note The code is based on de.tudarmstadt.ukp.dkpro.core.stanfordnlp.util.TreeUtils by Oliver Ferschke, which is
 *       Copyright 2013
 *       Ubiquitous Knowledge Processing (UKP) Lab
 *       Technische Universität Darmstadt
 *
 *       with license:
 *
 *       This program is free software: you can redistribute it and/or modify
 *       it under the terms of the GNU General Public License as published by
 *       the Free Software Foundation, either version 3 of the License, or
 *       (at your option) any later version.
 *
 *       This program is distributed in the hope that it will be useful,
 *       but WITHOUT ANY WARRANTY; without even the implied warranty of
 *       MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *       GNU General Public License for more details.
 *
 *       You should have received a copy of the GNU General Public License
 *       along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Oliver Ferschke
 * @author Tim Feuerbach
 */
object TreeBuilder {
  /**
   * Recursively creates a [[edu.berkeley.nlp.futile.syntax.Tree]] from a ROOT annotation of a CAS.
   *
   * @param root
     * the ROOT annotation
   * @return a [[edu.berkeley.nlp.futile.syntax.Tree]] object representing the syntax structure of the sentence
   */
  def buildTree(root: Annotation): Tree[String] = {
    val jCas = root.getCAS.getJCas

    root match {
      case node: Constituent if !isLeaf(node) =>
        val childNodes = new ArrayBuffer[Tree[String]]

        for (i <- 0 until node.getChildren.size) {
          childNodes += buildTree(node.getChildren(i))
        }

        new Tree[String](node.getConstituentType, childNodes.toIndexedSeq)

      case _ =>
        /*
        Handle leaf annotations. Leafs are always Token-annotations. We also have to insert a preterminal node with the
        value of the POS annotation on the token, since the POS is not directly stored within the tree.
         */
        val token = root.asInstanceOf[Token]
        val text = token.getCoveredText

        // get the pos annotations
        val coveredPos = JCasUtil.selectCovered(jCas, classOf[POS], token)
        // POS tags are required
        assert(coveredPos.size() > 0)

        val pos = coveredPos(0).getPosValue

        new Tree[String](pos, IndexedSeq(new Tree[String](text)))
    }
  }

  private def isLeaf(constituent: Constituent): Boolean = constituent.getChildren == null || constituent.getChildren.size() == 0
}
