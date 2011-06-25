/*
 * This file is part of the DITA Open Toolkit project hosted on
 * Sourceforge.net. See the accompanying license.txt file for 
 * applicable licenses.
 */

/*
 * (c) Copyright IBM Corp. 2004, 2005 All Rights Reserved.
 */
package org.dita.dost.reader;

import static org.dita.dost.util.Constants.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.dita.dost.exception.DITAOTXMLErrorHandler;
import org.dita.dost.log.MessageUtils;
import org.dita.dost.module.Content;
import org.dita.dost.module.ContentImpl;
import org.dita.dost.resolver.DitaURIResolverFactory;
import org.dita.dost.resolver.URIResolverAdapter;
import org.dita.dost.util.FileUtils;
import org.dita.dost.util.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;


/**
 * MapLinksReader reads and parse the index information. It is also used to parse
 * map link information in "maplinks.unordered" file.
 * 
 * NOTE: Result of this reader is a map organized as HashMap<String, HashMap<String, String> >.
 * Logically it is organized as:
 * 
 * 		topic-file-path --+-- topic-id-1 --+-- index-entry-1-1
 * 		                  |                +-- index-entry-1-2
 *                        |                +-- ...
 *                        |                +-- index-entry-1-n
 *                        |
 *                        +-- topic-id-1 --+-- ...
 *                        +-- ...
 *                        +-- topic-id-m --+-- ...
 * 
 * IF the input URL DOES NOT specify a topic ID explicitly, we use the special character '#' to
 * stand for the first topic ID this parser encounters.
 * 
 * @author Zhang, Yuan Peng
 */
public final class MapLinksReader extends AbstractXMLReader {
    private static final String INTERNET_LINK_MARK = "://";

    // check whether the index entries we got is meaningfull and valid
    private static boolean verifyIndexEntries(StringBuffer str) {
    	int start;
    	int end;
    	String temp;
        if (str.length() == 0) {
            return false;
        }
        start = str.indexOf(GREATER_THAN); // start from first tag's end
        end = str.lastIndexOf(LESS_THAN); // end at last tag's start

        /*
         * original code check whether there is text between different tags
         * modified code check whether there is any content between first and
         * last tags
         */
                
        temp = str.substring(start + 1, end);
        if (temp.trim().length() != 0) {
            return true;
        }
        return false;
    }
    private final List<String> ancestorList;
    private String filePath = null;
    private String firstMatchElement;
    private StringBuffer indexEntries;
    private File inputFile;
    private final HashSet<String> lastMatchElement;
    private int level;
    private final HashMap<String, HashMap<String,String> > map;
    private boolean match;

    /*
     * meta shows whether the event is in metadata when using sax to parse
     * ditmap file.
     */
    private final List<String> matchList;
    private boolean needResolveEntity;
    private XMLReader reader;
    private String topicPath;
    private boolean validHref;// whether the current href target is internal dita topic file
    

    /**
     * Default constructor of MapLinksReader class.
     */
    public MapLinksReader() {
        super();
        map = new HashMap<String, HashMap<String,String> >();
        ancestorList = new ArrayList<String>(INT_16);
        matchList = new ArrayList<String>(INT_16);
        indexEntries = new StringBuffer(INT_1024);
        firstMatchElement = null;
        lastMatchElement = new HashSet<String>();
        level = 0;
        match = false;
        validHref = true;
        needResolveEntity = false;
        topicPath = null;
        inputFile = null; 
        
        try {
            reader = StringUtils.getXMLReader();
            reader.setContentHandler(this);
            reader.setProperty(LEXICAL_HANDLER_PROPERTY,this);
            //Added by william on 2009-11-8 for ampbug:2893664 start
			reader.setFeature("http://apache.org/xml/features/scanner/notify-char-refs", true);
			reader.setFeature("http://apache.org/xml/features/scanner/notify-builtin-refs", true);
			//Added by william on 2009-11-8 for ampbug:2893664 end
			reader.setFeature("http://xml.org/sax/features/namespaces", false);
        } catch (final Exception e) {
        	logger.logException(e);
        }

    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {

        if (match && needResolveEntity && validHref) {
            final String temp = new String(ch, start, length);
            indexEntries.append(StringUtils.escapeXML(temp));
            
        }
    }

    // check whether the hierarchy of current node match the matchList
    private boolean checkMatch() {
        final int matchSize = matchList.size();
        final int ancestorSize = ancestorList.size();
        final ListIterator<String> matchIterator = matchList.listIterator();
        final ListIterator<String> ancestorIterator = ancestorList.listIterator(ancestorSize
                - matchSize);
        String currentMatchString;
        String ancestor;
        while (matchIterator.hasNext()) {
            currentMatchString = (String) matchIterator.next();
            ancestor = (String) ancestorIterator.next();
            if (!currentMatchString.contains(ancestor)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void endCDATA() throws SAXException {
    	if (match && validHref){
    		indexEntries.append(CDATA_END);
    	}
	    
	}

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {

        if (match) {
        	if (validHref){
        		indexEntries.append(LESS_THAN);
        		indexEntries.append(SLASH);
        		indexEntries.append(qName);
        		indexEntries.append(GREATER_THAN);
        	}
            
            level--;
        }

        if (lastMatchElement.contains(qName) && level == 0) {
            if (match) {
                match = false;
            }
        }
        if (!match) {
            ancestorList.remove(ancestorList.size() - 1);
        }

        if (qName.equals(firstMatchElement) && verifyIndexEntries(indexEntries) && topicPath != null) {
        	// if the href is not valid, topicPath will be null. We don't need to set the condition 
        	// to check validHref at here.
        	/*
                String origin = (String) map.get(topicPath);
                if (origin != null) {
                    map.put(topicPath, origin + indexEntries.toString());
                } else {
                    map.put(topicPath, indexEntries.toString());
                }
                indexEntries = new StringBuffer(INT_1024);
                */
        	String t = topicPath;
        	String frag = SHARP;
        	//Get topic id
        	if (t.contains(SHARP)) {
        		frag = t.indexOf(SHARP) + 1 >= t.length() ?
        				SHARP : t.substring(t.indexOf(SHARP) + 1);
        		//remove the "#" in topic file path
        		t = t.substring(0, t.indexOf(SHARP));
        	}
        	HashMap<String, String> m = (HashMap<String, String>)map.get(t);
        	if (m != null) {
        		final String orig = m.get(frag);
        		m.put(frag, StringUtils.setOrAppend(orig, indexEntries.toString(), false));
        	} else {
        		m = new HashMap<String, String>(INT_16);
        		m.put(frag, indexEntries.toString());
        		map.put(t, m);
        	}
        	//TODO Added by William on 2009-06-16 for bug:2791696 reltable bug start
        	indexEntries = new StringBuffer(INT_1024);
        	//TODO Added by William on 2009-06-16 for bug:2791696 reltable bug end
            
        }
    }

    @Override
    public void endEntity(String name) throws SAXException {
		if(!needResolveEntity){
			needResolveEntity = true;
		}
	}

    @Override
    public Content getContent() {

    	final ContentImpl result = new ContentImpl();
        result.setCollection( map.entrySet());
        return result;
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {

        if (match && validHref) {
            final String temp = new String(ch, start, length);
            indexEntries.append(temp);
           
        }
    }

    @Override
    public void read(String filename) {

        if (matchList.isEmpty()) {
        	logger.logError(MessageUtils.getMessage("DOTJ008E").toString());
        } else {
            match = false;
            needResolveEntity = true;
            inputFile = new File(filename);
            filePath = inputFile.getParent();
            inputFile.getPath();
            if(indexEntries.length() != 0){
				//delete all the content in indexEntries
				indexEntries = new StringBuffer(INT_1024);
            }
                         
            try {
            	reader.setErrorHandler(new DITAOTXMLErrorHandler(filename));
            	final InputSource source=URIResolverAdapter.convertToInputSource(DitaURIResolverFactory.getURIResolver().resolve(filename, null));
                reader.parse(source);
            } catch (final Exception e) {
            	logger.logException(e);
            }
        }
    }

    /**
     * Set the match pattern in the reader. The match pattern is used to see whether
     * current element can be include in the result of parsing.
     * 
     * @param matchPattern the match pattern
     */
    public void setMatch(String matchPattern) {
        int index = 0;
        firstMatchElement = (matchPattern.indexOf(SLASH) != -1) ? matchPattern.substring(0, matchPattern.indexOf(SLASH)) : matchPattern;
        
        while (index != -1) {
            final int start = matchPattern.indexOf(SLASH, index);
            final int end = matchPattern.indexOf(SLASH,start + 1);
            if(start != -1 && end != -1){
            	lastMatchElement.add(matchPattern.substring(start+1, end));
            	index = end;
            } else if(start != -1 && end == -1){
            	lastMatchElement.add(matchPattern.substring(start + 1));
            	index = -1;
            }
        }
        matchList.add(firstMatchElement);
        final Iterator<String> it = lastMatchElement.iterator();
        final StringBuffer sb = new StringBuffer();
        while(it.hasNext()){
        	sb.append(it.next() + STRING_BLANK);
        }
        matchList.add(sb.toString());
    }

    @Override
    public void startCDATA() throws SAXException {
    	if (match && validHref){
    		indexEntries.append(CDATA_HEAD);
    	}
	    
	}

    @Override
    public void startDTD(String name, String publicId, String systemId)
			throws SAXException {
        // NOOP
	}

    @Override
    public void startElement(String uri, String localName, String qName,
            Attributes atts) throws SAXException {
    	final int attsLen = atts.getLength();
    	final String attrScope = atts.getValue(ATTRIBUTE_NAME_SCOPE);
    	final String attrFormat = atts.getValue(ATTRIBUTE_NAME_FORMAT);
    	
        if (qName.equals(firstMatchElement)) {
            final String hrefValue = atts.getValue(ATTRIBUTE_NAME_HREF);
            if (verifyIndexEntries(indexEntries) && topicPath != null) {
            	/*
                String origin = (String) map.get(topicPath);
                map.put(topicPath, StringUtils.setOrAppend(origin, indexEntries.toString(), false));
                */
            	String t = topicPath;
            	String frag = SHARP;
            	if (t.contains(SHARP)) {
            		frag = t.indexOf(SHARP) + 1 >= t.length() ?
            				SHARP : t.substring(t.indexOf(SHARP) + 1);
            		t = t.substring(0, t.indexOf(SHARP));
            	}
            	HashMap<String, String> m = (HashMap<String, String>)map.get(t);
            	if (m != null) {
            		final String orig = m.get(frag);
            		m.put(frag, StringUtils.setOrAppend(orig, indexEntries.toString(), false));
            	} else {
            		m = new HashMap<String, String>(INT_16);
            		m.put(frag, indexEntries.toString());
            		map.put(t, m);
            	}
                indexEntries = new StringBuffer(INT_1024);
            }
            topicPath = null;
            if (hrefValue != null && hrefValue.indexOf(INTERNET_LINK_MARK) == -1
            		&& (attrScope == null || ATTR_SCOPE_VALUE_LOCAL.equalsIgnoreCase(attrScope))
            		&& (attrFormat == null || ATTR_FORMAT_VALUE_DITA.equalsIgnoreCase(attrFormat))) {
            	// If the href is internal dita topic file
            	topicPath = FileUtils.resolveTopic(filePath, hrefValue);
            	validHref = true;
            }else{
            	//set up the boolean to prevent the invalid href's metadata inserted into indexEntries.
            	topicPath = null;
            	validHref = false;
            }
        }
        if (!match) {
            ancestorList.add(qName);
            if (lastMatchElement.contains(qName) && checkMatch()) {
                
                    match = true;
                    level = 0;
            }
        }

        if (match) {
        	if (validHref){
	        	indexEntries.append(LESS_THAN + qName + STRING_BLANK);
	            
	            for (int i = 0; i < attsLen; i++) {
	            	indexEntries.append(atts.getQName(i));
	            	indexEntries.append(EQUAL);
					indexEntries.append(QUOTATION);
	            	indexEntries.append(StringUtils.escapeXML(atts.getValue(i)));
	            	indexEntries.append(QUOTATION);
	            	indexEntries.append(STRING_BLANK);
	            	
	            }
	            
	            indexEntries.append(GREATER_THAN);
        	}
            level++;
        }
    }

    @Override
    public void startEntity(String name) throws SAXException {
		needResolveEntity = StringUtils.checkEntity(name);
		if (match && !needResolveEntity && validHref) {
            indexEntries.append(StringUtils.getEntity(name));
            
        }
	}

	@Override
	public void processingInstruction(String target, String data)
			throws SAXException {		
		
		final String pi = (data != null) ? target + STRING_BLANK + data : target;
		
        if (match && needResolveEntity && validHref) {
            final String temp = LESS_THAN + QUESTION 
            + StringUtils.escapeXML(pi) + QUESTION + GREATER_THAN;
            indexEntries.append(temp);            
        }
        
	}
}
