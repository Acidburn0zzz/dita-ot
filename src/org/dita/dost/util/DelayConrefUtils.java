/*
 * This file is part of the DITA Open Toolkit project hosted on
 * Sourceforge.net. See the accompanying license.txt file for 
 * applicable licenses.
 */

/*
 * (c) Copyright IBM Corp. 2004, 2005 All Rights Reserved.
 */
package org.dita.dost.util;

import static org.dita.dost.util.Constants.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.dita.dost.log.DITAOTJavaLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * 
 * Delay conref feature related utility functions.
 * @author william
 *
 */
public final class DelayConrefUtils {
	
	private Document root = null;

	private final DITAOTJavaLogger javaLogger = new DITAOTJavaLogger();
	
	private static DelayConrefUtils instance = null;
	/**
	 * Return the DelayConrefUtils instance. Singleton.
	 * @return DelayConrefUtils
	 */
	public static synchronized DelayConrefUtils getInstance(){
		if(instance == null){
			instance = new DelayConrefUtils();
		}
		return instance;
	}
	
	
	
	/**
	 * Constructor.
	 */
	public DelayConrefUtils() {
		super();
		root = null;
	}



	/**
	 * Find whether an id is refer to a topic in a dita file.
	 * @param absolutePathToFile the absolute path of dita file
	 * @param id topic id
	 * @return true if id find and false otherwise
	 */
	public boolean findTopicId(final String absolutePathToFile, final String id) {
		
		if(!FileUtils.fileExists(absolutePathToFile)){
			return false;
		}
		try {
			//load the file
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			//factory.setFeature("http://xml.org/sax/features/validation", false);
			final DocumentBuilder builder = factory.newDocumentBuilder();
			try {
    			Class.forName(RESOLVER_CLASS);
    			builder.setEntityResolver(CatalogUtils.getCatalogResolver());
    		}catch (final ClassNotFoundException e){
    			builder.setEntityResolver(null);
    		}
			final Document root = builder.parse(new InputSource(new FileInputStream(absolutePathToFile)));
			
			//get root element
			final Element doc = root.getDocumentElement();
			//do BFS
			final Queue<Element> queue = new LinkedList<Element>();
			queue.offer(doc);
			while (!queue.isEmpty()) {
				final Element pe = queue.poll();
				final NodeList pchildrenList = pe.getChildNodes();
				for (int i = 0; i < pchildrenList.getLength(); i++) {
					final Node node = pchildrenList.item(i);
					if (node.getNodeType() == Node.ELEMENT_NODE) {
                        queue.offer((Element)node);
                    }
				}
				final String classValue = pe.getAttribute(ATTRIBUTE_NAME_CLASS);
				if(classValue!=null && classValue.contains(TOPIC_TOPIC.matcher)){
					//topic id found
					if(pe.getAttribute(ATTRIBUTE_NAME_ID).equals(id)){
						return true;
					}
				}
			}
			return false;
			
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		} catch (final SAXException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		} catch (final ParserConfigurationException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**check whether the href/id element defined by keys has been exported. 
	 * @param href href
	 * @param id id
	 * @param key keyname
	 * @param tempDir temp dir
	 * @return result list
	 */
	public List<Boolean> checkExport(String href, final String id, final String key, final String tempDir) {
		//parsed export .xml to get exported elements
		final String exportFile = (new File(tempDir, FILE_NAME_EXPORT_XML)).
		getAbsolutePath();
		
		boolean idExported = false;
		boolean keyrefExported = false;
		try {
			//load export.xml only once
			if(root==null){
				final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				//factory.setFeature("http://xml.org/sax/features/validation", false);
				final DocumentBuilder builder = factory.newDocumentBuilder();
				try {
	    			Class.forName(RESOLVER_CLASS);
	    			builder.setEntityResolver(CatalogUtils.getCatalogResolver());
	    		}catch (final ClassNotFoundException e){
	    			builder.setEntityResolver(null);
	    		}
				root = builder.parse(new InputSource(new FileInputStream(exportFile)));
			}
			//if dita file's extension name is ".xml"
			if(href.endsWith(FILE_EXTENSION_XML)){
				//change the extension to ".dita"
				href = href.replace(FILE_EXTENSION_XML, FILE_EXTENSION_DITA);
			}
			//get file node which contains the export node
			final Element fileNode = searchForKey(root.getDocumentElement(), href, "file");
			if(fileNode!=null){
				//iterate the child nodes
				final NodeList pList = fileNode.getChildNodes();
				for (int j = 0; j < pList.getLength(); j++) {
					final Node node = pList.item(j);
					if(Node.ELEMENT_NODE == node.getNodeType()){
						final Element child = (Element)node;
						//compare keys
						if(child.getNodeName().equals("keyref")&&
						   child.getAttribute(ATTRIBUTE_NAME_NAME)
						   .equals(key)){
							keyrefExported = true;
						//compare topic id
						}else if(child.getNodeName().equals("topicid")&&
							child.getAttribute(ATTRIBUTE_NAME_NAME)
							.equals(id)){
							idExported = true;
						//compare element id
						}else if(child.getNodeName().equals("id")&&
							child.getAttribute(ATTRIBUTE_NAME_NAME)
							.equals(id)){
							idExported = true;
						}
					}
					if(idExported && keyrefExported){
						break;
					}
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		final List<Boolean> list = new ArrayList<Boolean>();
		list.add(Boolean.valueOf(idExported));
		list.add(Boolean.valueOf(keyrefExported));
		return list;
	}
	/**
	 * Search specific element by key and tagName.
	 * @param root root element
	 * @param key search keyword
	 * @param tagName search tag name
	 * @return search result, null of either input is invalid or the looking result is not found.
	 */
	public Element searchForKey(final Element root, final String key, final String tagName) {
		if (root == null || StringUtils.isEmptyString(key)) {
            return null;
        }
		final Queue<Element> queue = new LinkedList<Element>();
		queue.offer(root);
		
		while (!queue.isEmpty()) {
			final Element pe = queue.poll();
			final NodeList pchildrenList = pe.getChildNodes();
			for (int i = 0; i < pchildrenList.getLength(); i++) {
				final Node node = pchildrenList.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
                    queue.offer((Element)node);
                }
			}
			String value = pe.getNodeName();
			if(StringUtils.isEmptyString(value)||
				!value.equals(tagName)){
				continue;
			}
			
			value = pe.getAttribute(ATTRIBUTE_NAME_NAME);
			if (StringUtils.isEmptyString(value)) {
                continue;
            }
			
			if (value.equals(key)) {
                return pe;
            }
		}
		return null;
	}
	/**
	 * Write map into xml file.
	 * @param m map
	 * @param outputFile output xml file
	 */
	public void writeMapToXML(final Map<String, Set<String>> m, final File outputFile) {

		if (m == null) {
            return;
        }
		final Properties prop = new Properties();
		final Iterator<Map.Entry<String, Set<String>>> iter = m.entrySet().iterator();
		while (iter.hasNext()) {
			final Map.Entry<String, Set<String>> entry = iter.next();
			final String key = entry.getKey();
			final String value = StringUtils.assembleString(entry.getValue(),
					COMMA);
			prop.setProperty(key, value);
		}
		//File outputFile = new File(tempDir, filename);
		
		final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		try {
			db = dbf.newDocumentBuilder();
		} catch (final ParserConfigurationException pce) {
			assert (false);
		}
		final Document doc = db.newDocument();
		final Element properties = (Element) doc.appendChild(doc
				.createElement("properties"));

		final Set<Object> keys = prop.keySet();
		final Iterator<Object> i = keys.iterator();
		while (i.hasNext()) {
			final String key = (String) i.next();
			final Element entry = (Element) properties.appendChild(doc
					.createElement("entry"));
			entry.setAttribute("key", key);
			entry.appendChild(doc.createTextNode(prop.getProperty(key)));
		}
		final TransformerFactory tf = TransformerFactory.newInstance();
		Transformer t = null;
		try {
			t = tf.newTransformer();
			t.setOutputProperty(OutputKeys.INDENT, "yes");
			t.setOutputProperty(OutputKeys.METHOD, "xml");
			t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		} catch (final TransformerConfigurationException tce) {
			assert (false);
		}
		final DOMSource doms = new DOMSource(doc);
        try {
        	final StreamResult sr = new StreamResult(new FileOutputStream(outputFile));
            t.transform(doms, sr);
        } catch (final TransformerException te) {
            this.javaLogger.logException(te);
        } catch (final IOException te) {
			this.javaLogger.logException(te);
		}
	}

}
