/**
 * Contains several classes for handling database/DCA configuration files
 *
 * Sets the schema for the XML configuration.
 */
@XmlSchema(namespace = "http://www.jobimtext.org/database", elementFormDefault = XmlNsForm.QUALIFIED)
package org.jobimtext.api.configuration;

import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;