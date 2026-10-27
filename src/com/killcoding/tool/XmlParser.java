/* ************************************************************************
The MIT License (MIT)
Copyright (c) 2024 KILLCODING.COM
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*****************************************************************************/

package com.killcoding.tool;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Attr;
import org.xml.sax.InputSource;

/**
* Lightweight XML-to-Map parser: converts an XML document into a nested Map<String,Object> tree
* (exposing the root element name) for script-friendly access.
* */
public class XmlParser {

    private String xml;
    private String rootName;
    private Map<String, Object> root;

    /**
    * Constructs a new XmlParser
    * @param xml - the xml
    * */
    @SuppressWarnings("this-escape")
    public XmlParser(String xml) throws Exception {
        super();
        this.xml = xml;
        root = xml2Map(this.xml);
    }
    
    /**
    * Performs the put operation
    * @param name - the name
    * @param String - the String
    * @param map - the map
    * */
    public void put(String name, Map<String, Object> map) {
        root.put(getShortName(name), map);
    }
    
    /**
    * Performs the getRootName operation
    * @return the getRootName result
    * */
    public String getRootName() {
        return rootName;
    }

    @SuppressWarnings("unchecked")
    public Object get(String... keys) {
        Object o = null;
        if (root == null)
            return null;

        Map<String, Object> m = root;
        for (int i = 0; i < keys.length; i++) {
            o = m.get(keys[i]);
            if (o == null) {
                break;
            } else {
                if (o instanceof Map) {
                    m = (Map<String, Object>) o;
                    continue;
                } else {
                    if (i != keys.length - 1)
                        o = null;
                }
            }
        }
        if (o != null && o instanceof String) {
            return ((String) o).trim();
        }
        return o;
    }
    
    @SuppressWarnings("unchecked")
    public List<Object> getList(String... keys) {
        List<Object> list = new ArrayList<Object>();
        Object o = null;
        if (root == null)
            return null;

        Map<String, Object> m = root;
        for (int i = 0; i < keys.length; i++) {
            o = m.get(keys[i]);
            if (o == null) {
                break;
            } else {
                if (o instanceof Map) {
                    m = (Map<String, Object>) o;
                    continue;
                } else {
                    if (i != keys.length - 1)
                        o = null;
                }
            }
        }
        if (o != null) {
            if (o instanceof String) {
                o = ((String) o).trim();
                list.add(o);
            }
            if (o instanceof List) {
                list.addAll((List) o);
            } else {
                list.add(o);
            }
        }
        return list;
    }

    /**
    * Performs the path operation
    * @param paths - the paths
    * @return the path result
    * */
    public Object path(String paths) {
        if (paths == null) return null;
        return get(paths.trim().split("\\."));
    }
    
    public List<Object> pathList(String paths) {
        if (paths == null) return null;
        return getList(paths.trim().split("\\."));
    }    

    public Map<String, Object> xml2Map(String xml) throws Exception {
        Map<String, Object> map = new HashMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xml)));
        
        Element rootElement = document.getDocumentElement();
        rootName = rootElement.getNodeName();
        
        NodeList childNodes = rootElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                processElement(element, map);
            }
        }
        return map;
    }

    /**
    * Performs the processElement operation
    * @param element - the element
    * @param String - the String
    * @param parentMap - the parentMap
    * */
    private void processElement(Element element, Map<String, Object> parentMap) {
        String elementName = element.getNodeName();
        NodeList children = element.getChildNodes();
        
        boolean hasElementChildren = false;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                hasElementChildren = true;
                break;
            }
        }
        
        if (!hasElementChildren) {
            String textContent = getElementText(element);
            handleValue(parentMap, elementName, textContent, element);
        } else {
            Map<String, Object> childMap = new HashMap<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    processElement((Element) child, childMap);
                }
            }
            
            if (childMap.isEmpty()) {
                String textContent = getElementText(element);
                handleValue(parentMap, elementName, textContent, element);
            } else {
                handleMap(parentMap, elementName, childMap, element);
            }
        }
    }

    /**
    * Performs the handleValue operation
    * @param String - the String
    * @param map - the map
    * @param key - the key
    * @param value - the value
    * @param element - the element
    * */
    @SuppressWarnings("unchecked")
    private void handleValue(Map<String, Object> map, String key, Object value, Element element) {
        Object existingValue = map.get(key);
        
        if (existingValue == null) {
            map.put(getShortName(key), blank(value));
            map.put("@" + getShortName(key), parseAttributes(element));
        } else if (existingValue instanceof List) {
            ((List<Object>) existingValue).add(blank(value));
            
            Object existingAttrs = map.get("@" + getShortName(key));
            if (existingAttrs instanceof List) {
                ((List<Object>) existingAttrs).add(parseAttributes(element));
            }
        } else {
            List<Object> valueList = new ArrayList<>();
            valueList.add(existingValue);
            valueList.add(blank(value));
            map.put(getShortName(key), valueList);
            
            List<Object> attrList = new ArrayList<>();
            attrList.add(map.get("@" + getShortName(key)));
            attrList.add(parseAttributes(element));
            map.put("@" + getShortName(key), attrList);
        }
    }

    /**
    * Performs the handleMap operation
    * @param String - the String
    * @param map - the map
    * @param key - the key
    * @param String - the String
    * @param value - the value
    * @param element - the element
    * */
    @SuppressWarnings("unchecked")
    private void handleMap(Map<String, Object> map, String key, Map<String, Object> value, Element element) {
        Object existingValue = map.get(key);
        
        if (existingValue == null) {
            map.put(getShortName(key), value);
            map.put("@" + getShortName(key), parseAttributes(element));
        } else if (existingValue instanceof List) {
            ((List<Object>) existingValue).add(value);
            
            Object existingAttrs = map.get("@" + getShortName(key));
            if (existingAttrs instanceof List) {
                ((List<Object>) existingAttrs).add(parseAttributes(element));
            }
        } else {
            List<Object> valueList = new ArrayList<>();
            valueList.add(existingValue);
            valueList.add(value);
            map.put(getShortName(key), valueList);
            
            List<Object> attrList = new ArrayList<>();
            attrList.add(map.get("@" + getShortName(key)));
            attrList.add(parseAttributes(element));
            map.put("@" + getShortName(key), attrList);
        }
    }

    public static Map<String, String> parseAttributes(Element element) {
        Map<String, String> atts = new HashMap<>();
        if (element.hasAttributes()) {
            for (int i = 0; i < element.getAttributes().getLength(); i++) {
                Attr attr = (Attr) element.getAttributes().item(i);
                atts.put(attr.getName(), attr.getValue());
            }
        }
        return atts;
    }
    
    /**
    * Performs the getElementText operation
    * @param element - the element
    * @return the getElementText result
    * */
    private String getElementText(Element element) {
        StringBuilder text = new StringBuilder();
        NodeList childNodes = element.getChildNodes();
        
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                text.append(node.getNodeValue());
            }
        }
        
        return text.toString();
    }
    
    public Map<String, Object> getRoot() {
        return root;
    }

    /**
    * Performs the blank operation
    * @param value - the value
    * @return the blank result
    * */
    private static Object blank(Object value) {
        return (value == null || value.toString().trim().equals("")) ? null : value;
    }
    
    /**
    * Performs the join operation
    * @param list - the list
    * @param conjunction - the conjunction
    * @return the join result
    * */
    protected static String join(List<String> list, String conjunction) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String item : list) {
            if (first)
                first = false;
            else
                sb.append(conjunction);
            
            sb.append(item.trim());
        }
        return sb.toString();
    }
    
    /**
    * Performs the getShortName operation
    * @param nodeName - the nodeName
    * @return the getShortName result
    * */
    private String getShortName(String nodeName) {
        return nodeName.replaceFirst("^.*:","");
    }    
    
}
    
