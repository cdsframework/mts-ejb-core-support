/**
 * The MTS core support EJB project is the base framework for the CDS Framework Middle Tier Service.
 *
 * Copyright (C) 2016 New York City Department of Health and Mental Hygiene, Bureau of Immunization
 * Contributions by HLN Consulting, LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version. You should have received a copy of the GNU Lesser
 * General Public License along with this program. If not, see <http://www.gnu.org/licenses/> for more
 * details.
 *
 * The above-named contributors (HLN Consulting, LLC) are also licensed by the New York City
 * Department of Health and Mental Hygiene, Bureau of Immunization to have (without restriction,
 * limitation, and warranty) complete irrevocable access and rights to this project.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; THE
 * SOFTWARE IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING,
 * BUT NOT LIMITED TO, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE COPYRIGHT HOLDERS, IF ANY, OR DEVELOPERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES, OR OTHER LIABILITY OF ANY KIND, ARISING FROM, OUT OF, OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information about this software, see https://www.hln.com/services/open-source/ or send
 * correspondence to ice@hln.com.
 */
package org.cdsframework.ejb.local;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.util.LogUtils;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.filter.LoggingFilter;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author sdn
 */
@LocalBean
@Stateless
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class JasperServerMGRLocal {

    final private LogUtils logger = LogUtils.getLogger(JasperServerMGRLocal.class);
    @EJB
    private PropertyMGRLocal propertyMGRLocal;

    /**
     * Get a new instance of WebTarget for calling the Jasper REST API
     *
     * @param client
     * @return a new instance of WebTarget
     */
    public WebTarget getJasperServerBaseUri(Client client) {
        final String METHODNAME = "getJasperServerBaseUri ";
        WebTarget webTarget;

        // build the URI
        String baseUri = "http";
        Boolean isSSl = propertyMGRLocal.get("JASPER_SERVER_SSL", Boolean.class);
        if (isSSl) {
            baseUri += "s";
        }
        baseUri += "://";
        String host = propertyMGRLocal.get("JASPER_SERVER_HOST", String.class);
        baseUri += host + ":";
        String port = propertyMGRLocal.get("JASPER_SERVER_PORT", String.class);
        baseUri += port;
        String base = propertyMGRLocal.get("JASPER_SERVER_ROOT", String.class);
        baseUri += base;

        logger.info(METHODNAME, "baseUri=", baseUri);
        
        // initialize the target
        webTarget = client.target(baseUri);

        // initialize basic auth layer is appropriate
        String username = propertyMGRLocal.get("JASPER_SERVER_USERNAME", String.class);
        String password = propertyMGRLocal.get("JASPER_SERVER_PASSWORD", String.class);
        
        if (username != null && password != null) {
            webTarget.register(HttpAuthenticationFeature.basic(username, password));
        }
        
//        java.util.logging.Logger log = java.util.logging.Logger.getLogger(JasperServerMGRLocal.class.getSimpleName());
        webTarget.register(new LoggingFilter());
//        webTarget.register(new LoggingFilter(log, true));
        

        return webTarget;
    }

    public InputStream getPdfReport(String reportURI, Map<String, Object> parameters) throws MtsException {
        return getInputStreamReport(reportURI + ".pdf", parameters);
    }
    
    public InputStream getXlsReport(String reportURI, Map<String, Object> parameters) throws MtsException {
        return getInputStreamReport(reportURI + ".xls", parameters);
    }
    
    private InputStream getInputStreamReport(String reportURI, Map<String, Object> parameters) throws MtsException {
        final String METHODNAME = "getInputStreamReport ";
        logger.info(METHODNAME, "reportURI", reportURI);
        // initialize if null
        if (parameters == null) {
            parameters = new HashMap<String, Object>();
        }

        // do not ignore pagination in PDF
        parameters.put("ignorePagination", "false");

        // get a new client
        Client client = ClientBuilder.newClient();

        // get the base web target
        WebTarget webTarget = getJasperServerBaseUri(client);

        // this uses the rest v2 reports service
        webTarget = webTarget.path("/rest_v2/reports");

        // tack on the report path + the output type
        webTarget = webTarget.path(reportURI);

        // get output
        try {
            // Setup the query parameters
            webTarget = getJasperQueryParameters(webTarget, parameters);
            logger.info(METHODNAME, "webTarget.getUri()=", webTarget.getUri());
            InputStream inputStream = webTarget.request(MediaType.APPLICATION_OCTET_STREAM).get(InputStream.class);
            return inputStream;
        }
        catch (Exception e) {
            logger.error(METHODNAME, "An Exception has occurred: Message: ", e.getMessage(), e);
            throw new MtsException("An Exception has occurred: Message: " + e.getMessage());
        }
        finally {
            // close the connection
            client.close();
        }
    }
    
    private WebTarget getJasperQueryParameters(WebTarget webTarget, Map<String, Object> parameters) throws MtsException, UnsupportedEncodingException {
        final String METHODNAME = "setJasperQueryParameters ";
        
        // add the parameters as query string arguments
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            Object oValue = entry.getValue();
            
            // Handle ArrayList
            if (oValue instanceof ArrayList) {
                List objects = (ArrayList) oValue;
                if (!objects.isEmpty() && objects.get(0) instanceof String) {
                    List<String> strings = (ArrayList<String>) objects;
                    for (String string : strings) {
                        String[] values = new String[]{ URLEncoder.encode(string, "UTF-8").replace("+", "%20") };
                        webTarget = webTarget.queryParam(entry.getKey(), (Object[]) values);
                        // Add it again Jasper Report needs a collection
                        if (strings.size() == 1) {
                            webTarget = webTarget.queryParam(entry.getKey(), (Object[]) values);
                        }
                    }
                }
                else {
                    throw new MtsException("Jasper Query Parameter " + entry.getKey() + " must be a String data type, CanonicalName() " + 
                            entry.getValue().getClass().getCanonicalName());
                }
            }
            else if (oValue instanceof String) {
                String[] values = new String[]{ URLEncoder.encode((String) oValue, "UTF-8").replace("+", "%20") };
                webTarget = webTarget.queryParam(entry.getKey(), (Object[]) values);
            }
            else {
                throw new MtsException("Jasper Query Parameter " + entry.getKey() + " must be a String data type, CanonicalName() " + 
                        entry.getValue().getClass().getCanonicalName());
            }
        }
        return webTarget;
    }

    /**
     * Find the node which contains the jrPage class which represents the report contents.
     *
     * @param node
     * @return
     */
    private Node getJrPage(Node node) {
        final String METHODNAME = "getJrPage ";
        Node result = null;
        if (node != null) {
            boolean jrPageFound = false;
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                for (int c = 0; c < attributes.getLength(); c++) {
                    if ("jrPage".equalsIgnoreCase(attributes.item(c).getNodeValue())) {
                        jrPageFound = true;
                    }
                    logger.debug(METHODNAME,
                            "node attribute: ", attributes.item(c).getNodeName(),
                            "; ", attributes.item(c).getNodeType(),
                            "; ", attributes.item(c).getNodeValue());
                }
            } else {
                logger.debug(METHODNAME, "attributes is null");
            }
            if (!jrPageFound) {
                NodeList childNodes = node.getChildNodes();
                if (childNodes != null) {
                    for (int i = 0; i < childNodes.getLength(); i++) {
                        result = getJrPage(childNodes.item(i));
                        if (result != null) {
                            break;
                        }
                    }
                } else {
                    logger.debug(METHODNAME, "childNodes is null");
                }
            } else {
                result = node;
            }
        } else {
            logger.error(METHODNAME, "node is null");
        }
        return result;
    }
}
