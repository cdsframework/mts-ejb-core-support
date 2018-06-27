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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.dto.UserDTO;
import org.cdsframework.util.LogUtils;
import org.cdsframework.util.StringUtils;

/**
 *
 * @author HLN Consulting, LLC
 */
@LocalBean
@Stateless
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class SmtpMGRLocal {

    private final static LogUtils logger = LogUtils.getLogger(SmtpMGRLocal.class);
    @EJB
    private PropertyMGRLocal propertyMGRLocal;

    private String getFirstEmailFromSession(SessionDTO sessionDTO) {
        final String METHODNAME = "getFirstEmailFromSession ";
        String toAddress = null;
        String username = null;
        if (sessionDTO != null) {
            UserDTO userDTO = sessionDTO.getUserDTO();
            if (userDTO != null) {
                username = userDTO.getUsername();
                toAddress = userDTO.getEmail();
            } else {
                logger.error(METHODNAME, "session UserDTO was null!");
            }
        } else {
            logger.error(METHODNAME, "session was null!");
        }
        if (toAddress == null) {
            logger.error(METHODNAME, "could not find an email for the session user: ", username);
        }
        return toAddress;
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param sessionDTO the session that contains a UserDTO/ContactDTO/ContactEmailDTO...
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param overrideDevMode dev mode causes the from address to be ignored and all email is sent to a development email account.
     */
    public void sendMessageToSessionUser(String addrFrom,
            SessionDTO sessionDTO,
            String msgSubject,
            String plainText,
            boolean overrideDevMode) {
        final String METHODNAME = "sendMessageToSessionUser ";
        String toAddress = getFirstEmailFromSession(sessionDTO);
        if (toAddress != null) {
            sendMessage(addrFrom, toAddress, msgSubject, plainText, overrideDevMode);
        } else {
            logger.error(METHODNAME, "not sending message - no email address to send to!");
        }
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     */
    public void sendMessage(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText) {
        sendMessage(addrFrom, addrTo, msgSubject, plainText, false);
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param overrideDevMode dev mode causes the from address to be ignored and all email is sent to a development email account.
     */
    public void sendMessage(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText,
            boolean overrideDevMode) {
        sendHtmlMessageWithAttachment(addrFrom, addrTo, msgSubject, plainText, null, new ArrayList<DataSource>(), overrideDevMode);
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param attachment the attachment
     */
    public void sendMessageWithAttachment(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText,
            DataSource attachment) {
        sendMessageWithAttachment(addrFrom, addrTo, msgSubject, plainText, attachment, false);
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param attachment the attachment
     * @param overrideDevMode dev mode causes the from address to be ignored and all email is sent to a development email account.
     */
    public void sendMessageWithAttachment(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText,
            DataSource attachment,
            boolean overrideDevMode) {
        List<DataSource> attachments = new ArrayList<DataSource>();
        attachments.add(attachment);
        sendHtmlMessageWithAttachment(addrFrom, addrTo, msgSubject, plainText, null, attachments, overrideDevMode);
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param attachments the attachment list
     */
    public void sendMessageWithAttachment(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText,
            List<DataSource> attachments) {
        sendMessageWithAttachment(addrFrom, addrTo, msgSubject, plainText, attachments, false);
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param attachments the attachment list
     * @param overrideDevMode dev mode causes the from address to be ignored and all email is sent to a development email account.
     */
    public void sendMessageWithAttachment(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText,
            List<DataSource> attachments,
            boolean overrideDevMode) {
        sendHtmlMessageWithAttachment(addrFrom, addrTo, msgSubject, plainText, null, attachments, overrideDevMode);
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param htmlText the message html body
     */
    public void sendHtmlMessage(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText,
            String htmlText) {
        sendHtmlMessage(addrFrom, addrTo, msgSubject, plainText, htmlText, false);
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param htmlText the message html body
     * @param overrideDevMode dev mode causes the from address to be ignored and all email is sent to a development email account.
     */
    public void sendHtmlMessage(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText,
            String htmlText,
            boolean overrideDevMode) {
        sendHtmlMessageWithAttachment(addrFrom, addrTo, msgSubject, plainText, htmlText, new ArrayList<DataSource>(), overrideDevMode);
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param htmlText the message html body
     * @param attachment the attachment list
     */
    public void sendHtmlMessageWithAttachment(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText,
            String htmlText,
            DataSource attachment) {
        sendHtmlMessageWithAttachment(addrFrom, addrTo, msgSubject, plainText, htmlText, attachment, false);
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param htmlText the message html body
     * @param attachment the attachment
     * @param overrideDevMode dev mode causes the from address to be ignored and all email is sent to a development email account.
     */
    public void sendHtmlMessageWithAttachment(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText,
            String htmlText,
            DataSource attachment,
            boolean overrideDevMode) {
        List<DataSource> attachments = new ArrayList<DataSource>();
        attachments.add(attachment);
        sendHtmlMessageWithAttachment(addrFrom, addrTo, msgSubject, plainText, htmlText, attachments, overrideDevMode);
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param htmlText the message html body
     * @param attachments the attachment list
     */
    public void sendHtmlMessageWithAttachment(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText,
            String htmlText,
            List<DataSource> attachments) {
        sendHtmlMessageWithAttachment(addrFrom, addrTo, msgSubject, plainText, htmlText, attachments, false);
    }

    /**
     *
     * @param addrFrom the message address from field; if null a default is used.
     * @param addrTo the message address to field - supports comma separated addresses
     * @param msgSubject the message subject
     * @param plainText the message plain text body
     * @param htmlText the message HTML body
     * @param attachments the attachment list
     * @param overrideDevMode dev mode causes the from address to be ignored and all email is sent to a development email account.
     */
    public void sendHtmlMessageWithAttachment(String addrFrom,
            String addrTo,
            String msgSubject,
            String plainText,
            String htmlText,
            List<DataSource> attachments,
            boolean overrideDevMode) {
        final String METHODNAME = "sendHtmlMessageWithAttachment ";
        String smtpHost = propertyMGRLocal.get("SMTP_HOST", String.class);
        String smtpPort = propertyMGRLocal.get("SMTP_PORT", String.class);
        String testEmailToAddress = propertyMGRLocal.get("TEST_EMAIL_TO_ADDRESS", String.class);
        String fromAddress = addrFrom;
        if (fromAddress == null) {
            fromAddress = propertyMGRLocal.get("DEFAULT_FROM_ADDRESS", String.class);
        }
        List<String> recipients = new ArrayList<String>();
        // Override addrTo address
        if (!overrideDevMode && !StringUtils.isEmpty(testEmailToAddress)) {
            recipients.addAll(Arrays.asList(testEmailToAddress.split(",")));
            logger.info(METHODNAME, "using dev mode address.");
        } else {
            recipients.addAll(Arrays.asList(addrTo.split(",")));
            logger.info(METHODNAME, "using supplied address.");
        }
        logger.info(METHODNAME, "recipient list: ", recipients);
        Properties props = new Properties();
        logger.info(METHODNAME, "Sending message via host: ", smtpHost);
        logger.info(METHODNAME, "Sending message via port: ", smtpPort);
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        Session session = Session.getDefaultInstance(props, null);
        try {
            // create a message
            logger.info("Creating mail message:\n"
                    + "   from=" + fromAddress + "\n"
                    + "   to=" + recipients + "\n"
                    + "   subject=" + msgSubject + "\n"
                    + "   plainText=" + plainText + "\n"
                    + "   attachmentFileNames=" + attachments.toString());

            // Define message
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress));
            for (String recipient : recipients) {
                if (!recipient.trim().isEmpty()) {
                    logger.info(METHODNAME, "Adding address: ", recipient);
                    message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient.trim()));
                } else {
                    logger.warn(METHODNAME, "excluding empty address...");
                }
            }
            message.setSubject(msgSubject);

            Multipart multipart = new MimeMultipart("alternative");

            // the HTML message
            if (htmlText != null) {
                MimeBodyPart htmlMessage = new MimeBodyPart();
                htmlMessage.setContent(htmlText, "text/html");
                multipart.addBodyPart(htmlMessage);
            }

            // the plain (non-HTML) alternate message
            if (plainText != null) {
                MimeBodyPart plainTextBodyPart = new MimeBodyPart();
                plainTextBodyPart.setHeader("MIME-Version", "1.0");
                plainTextBodyPart.setHeader("Content-Type", plainTextBodyPart.getContentType());
                plainTextBodyPart.setText(plainText);
                multipart.addBodyPart(plainTextBodyPart);
            }

            // now the attachments
            for (DataSource attachment : attachments) {
                MimeBodyPart attachBodyPart = new MimeBodyPart();
                attachBodyPart.setDataHandler(new DataHandler(attachment));
                attachBodyPart.setFileName(attachment.getName());
                multipart.addBodyPart(attachBodyPart);
            }

            // Put parts in message
            message.setContent(multipart);

            // Send the message
            Transport.send(message);
            logger.info("sendHtmlMessage(): message sent.");
        } catch (MessagingException mex) {
            logger.error("sendHtmlMessage(): msg=" + mex.getMessage(), mex);

            Exception ex = mex;
            do {
                if (ex instanceof SendFailedException) {
                    SendFailedException sfex = (SendFailedException) ex;
                    Address[] invalid = sfex.getInvalidAddresses();
                    if (invalid != null) {
                        logger.error("    ** Invalid Addresses");
                        for (int i = 0; i < invalid.length; i++) {
                            logger.error("         " + invalid[i]);
                        }
                    }
                    Address[] validUnsent = sfex.getValidUnsentAddresses();
                    if (validUnsent != null) {
                        logger.error("    ** ValidUnsent Addresses");
                        for (int i = 0; i < validUnsent.length; i++) {
                            logger.error("         " + validUnsent[i]);
                        }
                    }
                    Address[] validSent = sfex.getValidSentAddresses();
                    if (validSent != null) {
                        logger.error("    ** ValidSent Addresses");
                        for (int i = 0; i < validSent.length; i++) {
                            logger.error("         " + validSent[i]);
                        }
                    }
                }
                if (ex instanceof MessagingException) {
                    ex = ((MessagingException) ex).getNextException();
                } else {
                    ex = null;
                }
            } while (ex != null);
        }
    }
}
