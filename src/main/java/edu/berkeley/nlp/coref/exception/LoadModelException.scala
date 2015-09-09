package edu.berkeley.nlp.coref.exception

import java.io.IOException

/**
 * Thrown if a precomputed model could not be loaded.
 *
 * @author Tim Feuerbach
 */
class LoadModelException(val message: String, val cause: Exception) extends IOException(message, cause) {

}
