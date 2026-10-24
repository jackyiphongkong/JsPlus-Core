package com.killcoding.tool;

import java.io.IOException;
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

public class XmlParser {

    private String xml;
    private String rootName;
    private Map<String, Object> root;

    public XmlParser(String xml) throws Exception {
        super();
        this.xml = xml;
        root = xml2Map(this.xml);
    }
    
    public void put(String name, Map<String, Object> map) {
        root.put(getShortName(name), map);
    }
    
    public String getRootName() {
        return rootName;
    }

    public Object get(String... keys) {
        Object o = null;
        if (root == null)
            return null;

        Map<String, Object> m = (Map<String, Object>) root;
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
    
    public List<Object> getList(String... keys) {
        List<Object> list = new ArrayList<Object>();
        Object o = null;
        if (root == null)
            return null;

        Map<String, Object> m = (Map<String, Object>) root;
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

    private static Object blank(Object value) {
        return (value == null || value.toString().trim().equals("")) ? null : value;
    }
    
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
    
    private String getShortName(String nodeName) {
        return nodeName.replaceFirst("^.*:","");
    }    
    
}
    
