/*
 * This file is part of the DITA Open Toolkit project hosted on
 * Sourceforge.net. See the accompanying license.txt file for 
 * applicable licenses.
 */

/*
 * (c) Copyright IBM Corp. 2004, 2005 All Rights Reserved.
 */
package org.dita.dost.writer;

import static org.dita.dost.util.Constants.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

import org.dita.dost.exception.DITAOTXMLErrorHandler;
import org.dita.dost.log.MessageUtils;
import org.dita.dost.module.Content;
import org.dita.dost.util.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;


/*
 * Created on 2004-12-17
 */

/**
 * DitaIndexWriter reads dita topic file and insert the index information into it.
 * 
 * @author Zhang, Yuan Peng
 */
public final class DitaIndexWriter extends AbstractXMLWriter {
    private String firstMatchTopic;
    private boolean hasMetadataTillNow;// whether we have met <metadata> in <prolog> element
    private boolean hasPrologTillNow;// whether we have met <prolog> in this topic we want

    private String indexEntries;
    private String lastMatchTopic;
    private List<String> matchList; // topic path that topicIdList need to match
    private boolean needResolveEntity;
    private OutputStreamWriter output;
    private XMLReader reader;
    private boolean startTopic; //whether to insert links at this topic
    private List<String> topicIdList; // array list that is used to keep the hierarchy of topic id
    private boolean insideCDATA;
    private boolean hasWritten;
    private ArrayList<String> topicSpecList = new ArrayList<String>();
    private int topicLevel = -1;


    /**
     * Default constructor of DitaIndexWriter class.
     */
    public DitaIndexWriter() {
        super();
        topicIdList = new ArrayList<String>(INT_16);
        firstMatchTopic = null;
        hasMetadataTillNow = false;
        hasPrologTillNow = false;
        indexEntries = null;
        lastMatchTopic = null;
        matchList = null;
        needResolveEntity = false;
        output = null;
        startTopic = false;
        insideCDATA = false;
        hasWritten = false;
        
        try {
            reader = StringUtils.getXMLReader();
            reader.setContentHandler(this);
            reader.setProperty(LEXICAL_HANDLER_PROPERTY,this);
            reader.setFeature(FEATURE_NAMESPACE_PREFIX, true);
            //Edited by william on 2009-11-8 for ampbug:2893664 start
			reader.setFeature("http://apache.org/xml/features/scanner/notify-char-refs", true);
			reader.setFeature("http://apache.org/xml/features/scanner/notify-builtin-refs", true);
			//Edited by william on 2009-11-8 for ampbug:2893664 end
        } catch (Exception e) {
        	logger.logException(e);
        }

    }


    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
    	if(needResolveEntity){
    		try {
    			if(insideCDATA)
    				output.write(ch, start, length);
    			else
    				output.write(StringUtils.escapeXML(ch, start, length));
        	} catch (Exception e) {
        		logger.logException(e);
        	}
    	}
    }
    
//  check whether the hierarchy of current node match the matchList
    private boolean checkMatch() {    	
		if (matchList == null){
			return true;
		}
        int matchSize = matchList.size();
        int ancestorSize = topicIdList.size();
        ListIterator<String> matchIterator = matchList.listIterator();
        ListIterator<String> ancestorIterator = topicIdList.listIterator(ancestorSize
                - matchSize);
        String match;
        String ancestor;
                
        while (matchIterator.hasNext()) {
            match = matchIterator.next();
            ancestor = ancestorIterator.next();
            if (!match.equals(ancestor)) {
                return false;
            }
        }
        return true;
    }

	@Override
    public void endCDATA() throws SAXException {
    	insideCDATA = false;
	    try{
	        output.write(CDATA_END);
	    }catch(Exception e){
	    	logger.logException(e);
	    }
	}

    @Override
    public void endDocument() throws SAXException {

        try {
            output.flush();
        } catch (Exception e) {
        	logger.logException(e);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (!startTopic){
            topicIdList.remove(topicIdList.size() - 1);
        }
        try {
        	if (topicSpecList.contains(localName)){
        		topicLevel--;
        	}
        	
            if (!hasMetadataTillNow && ELEMENT_NAME_PROLOG.equals(qName) && startTopic && !hasWritten) {
                output.write(META_HEAD);
                output.write(indexEntries);
                output.write(META_END);
                hasMetadataTillNow = true;
                hasWritten = true;
            }
            output.write(LESS_THAN + SLASH + qName
                    + GREATER_THAN);
            if (!hasPrologTillNow && startTopic && !hasWritten && !topicSpecList.contains(localName)) {
                // if <prolog> don't exist
            	output.write(System.getProperty("line.separator"));
                output.write(PROLOG_HEAD + META_HEAD);
                output.write(indexEntries);
                output.write(META_END + PROLOG_END);
                hasPrologTillNow = true;
                hasWritten = true;
            }
            
        } catch (Exception e) {
        	logger.logException(e);
        }
    }

	@Override
    public void endEntity(String name) throws SAXException {
		if(!needResolveEntity){
			needResolveEntity = true;
		}
	}
	
	private boolean hasMetadata(String qName){
		//check whether there is <metadata> in <prolog> element
		//if there is <prolog> element and there is no <metadata> element before
		//and current element is <resourceid>, then there is no <metadata> in current
		//<prolog> element. return false.
		if(hasPrologTillNow && !hasMetadataTillNow && ELEMENT_NAME_RESOURCEID.equals(qName)){
			return false;
		}
		return true;
	}
	
	private boolean hasProlog(Attributes atts){
		//check whether there is <prolog> in the current topic
		//if current element is <body> and there is no <prolog> before
		//then this topic has no <prolog> and return false
		
		if (atts.getValue(ATTRIBUTE_NAME_CLASS) != null){
			if (!hasPrologTillNow){
				if (atts.getValue(ATTRIBUTE_NAME_CLASS).indexOf(" topic/body ") != -1){
					return false;
				}
				else if (atts.getValue(ATTRIBUTE_NAME_CLASS).indexOf(" topic/related-links ") != -1){
					return false;
				}
				else if (atts.getValue(ATTRIBUTE_NAME_CLASS).indexOf(" topic/topic ") != -1){

					if (topicLevel > 0){
						topicLevel++;
					}else if (topicLevel == -1){ 
						topicLevel = 1;
					}else {
						return false;
					}
					return false;  //Eric
					
				}
			}
		}
		return true;		
	}

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        try {
            output.write(ch, start, length);
        } catch (Exception e) {
        	logger.logException(e);
        }
    }

    @Override
    public void processingInstruction(String target, String data)
            throws SAXException {
        String pi;
        try {
            pi = (data != null) ? target + STRING_BLANK + data : target;
            output.write(LESS_THAN + QUESTION 
                    + pi + QUESTION + GREATER_THAN);
        } catch (Exception e) {
        	logger.logException(e);
        }
    }

    @Override
    public void setContent(Content content) {
        indexEntries = (String) content.getValue();
    }
    private void setMatch(String match) {
		int index = 0;
        matchList = new ArrayList<String>(INT_16);
        
        firstMatchTopic = (match.indexOf(SLASH) != -1) ? match.substring(0, match.indexOf('/')) : match;

        while (index != -1) {
            int end = match.indexOf(SLASH, index);
            if (end == -1) {
                matchList.add(match.substring(index));
                lastMatchTopic = match.substring(index);
                index = end;
            } else {
                matchList.add(match.substring(index, end));
                index = end + 1;
            }
        }
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        try {
            output.write(StringUtils.getEntity(name));
        } catch (Exception e) {
        	logger.logException(e);
        }
    }
	
	@Override
    public void startCDATA() throws SAXException {
    	insideCDATA = true;
	    try{
	        output.write(CDATA_HEAD);
	    }catch(Exception e){
	    	logger.logException(e);
	    }
	}

    @Override
    public void startElement(String uri, String localName, String qName,
            Attributes atts) throws SAXException {
    	int attsLen = atts.getLength();
    	
        try {
        	if (topicLevel != -1){
	            if (!hasProlog(atts) && startTopic && !hasWritten) {
	                // if <prolog> don't exist
	            	output.write(System.getProperty("line.separator"));
	            	output.write(PROLOG_HEAD + META_HEAD);
	                output.write(indexEntries);
	                output.write(META_END + PROLOG_END);
	                hasPrologTillNow = true;
	                hasWritten = true;
	            }
        	}
            if ( !startTopic && !ELEMENT_NAME_DITA.equalsIgnoreCase(qName)){
                if (atts.getValue(ATTRIBUTE_NAME_ID) != null){
                    topicIdList.add(atts.getValue(ATTRIBUTE_NAME_ID));
                }else{
                    topicIdList.add("null");
                }
                if (topicIdList.size() >= matchList.size()){
                //To access topic by id globally
                    startTopic = checkMatch();
                }
            }

            if (!hasMetadata(qName) && startTopic && !hasWritten) {
                output.write(META_HEAD);
                output.write(indexEntries);
                output.write(META_END);
                hasMetadataTillNow = true;
                hasWritten = true;
            }

            output.write(LESS_THAN + qName);
            for (int i = 0; i < attsLen; i++) {
                String attQName = atts.getQName(i);
                String attValue;
                attValue = atts.getValue(i);
                
                // replace '&' with '&amp;'
				if (attValue.indexOf('&') > 0) {
					attValue = StringUtils.replaceAll(attValue, "&", "&amp;");
				}
                
                output.write(new StringBuffer().append(STRING_BLANK)
                		.append(attQName).append(EQUAL).append(QUOTATION)
                		.append(attValue).append(QUOTATION).toString());
            }
            output.write(GREATER_THAN);
            if (atts.getValue(ATTRIBUTE_NAME_CLASS) != null){
            	
	            if (atts.getValue(ATTRIBUTE_NAME_CLASS)
	                    .indexOf(" topic/metadata ") != -1 && startTopic && !hasWritten) {
	                hasMetadataTillNow = true;
	                output.write(indexEntries);
	                hasWritten = true;
	            }
	            if (atts.getValue(ATTRIBUTE_NAME_CLASS)
	                    .indexOf(" topic/prolog ") != -1) {
	                hasPrologTillNow = true;
	            }
            }
        } catch (Exception e) {
        	logger.logException(e);
        }
    }

	@Override
    public void startEntity(String name) throws SAXException {
		try {
           	needResolveEntity = StringUtils.checkEntity(name);
           	if(!needResolveEntity){
           		output.write(StringUtils.getEntity(name));
           	}
        } catch (Exception e) {
        	logger.logException(e);
        }
	}

    @Override
    public void write(String outputFilename) {
    	String filename = outputFilename;
		String file = null;
		String topic = null;
		File inputFile = null;
		File outputFile = null;
		FileOutputStream fileOutput = null;

        try {
            if(filename.endsWith(SHARP)){
            	filename = filename.substring(0, filename.length()-1);
            }
            
            if(filename.lastIndexOf(SHARP)!=-1){
                file = filename.substring(0,filename.lastIndexOf(SHARP));
                topic = filename.substring(filename.lastIndexOf(SHARP)+1);
                setMatch(topic);
                startTopic = false;
            }else{
                file = filename;
                matchList = null;
                startTopic = true;
            }
        	needResolveEntity = true;
            hasPrologTillNow = false;
            hasMetadataTillNow = false;
            hasWritten = false;
            inputFile = new File(file);
            outputFile = new File(file + FILE_EXTENSION_TEMP);
            fileOutput = new FileOutputStream(outputFile);
            output = new OutputStreamWriter(fileOutput, UTF8);

            topicIdList.clear();
            reader.setErrorHandler(new DITAOTXMLErrorHandler(file));
            reader.parse(file);

            output.close();
            if(!inputFile.delete()){
            	Properties prop = new Properties();
            	prop.put("%1", inputFile.getPath());
            	prop.put("%2", outputFile.getPath());
            	logger.logError(MessageUtils.getMessage("DOTJ009E", prop).toString());
            }
            if(!outputFile.renameTo(inputFile)){
            	Properties prop = new Properties();
            	prop.put("%1", inputFile.getPath());
            	prop.put("%2", outputFile.getPath());
            	logger.logError(MessageUtils.getMessage("DOTJ009E", prop).toString());
            }
        } catch (Exception e) {
        	logger.logException(e);
        }finally {
            try{
                fileOutput.close();
            } catch (Exception e) {
            	logger.logException(e);
            }
        }
    }
}
