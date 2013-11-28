/*******************************************************************************
 * Copyright (C) 2009-2011 FuseSource Corp.
 * Copyright (c) 2008 IBM Corporation and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.fusesource.hawtjni.generator;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.fusesource.hawtjni.generator.HawtJNI.UsageException;
import org.fusesource.hawtjni.generator.util.FileSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * 
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class MacGenerator {
    String[] xmls;
    Document[] documents;
    String outputDir, mainClassName;
    String delimiter = System.getProperty("line.separator");
    PrintStream out;

    public MacGenerator() {
    }

    static void list(File path, ArrayList<String> list) {
        if (path == null)
            return;
        File[] frameworks = path.listFiles();
        if (frameworks == null)
            return;
        for (int i = 0; i < frameworks.length; i++) {
            File file = frameworks[i];
            String name = file.getName();
            int index = name.lastIndexOf(".");
            if (index != -1) {
                String xml = file.getAbsolutePath() + "/Resources/BridgeSupport/" + name.substring(0, index) + "Full.bridgesupport";
                if (new File(xml).exists()) {
                    list.add(xml);
                }
            }
        }
    }

    int getLevel(Node node) {
        int level = 0;
        while (node != null) {
            level++;
            node = node.getParentNode();
        }
        return level;
    }

    void merge(Document document, Document extraDocument) {
        if (extraDocument == null)
            return;

        /* Build a lookup table for extraDocument */
        HashMap<String, Node> extras = new HashMap<String, Node>();
        buildLookup(extraDocument, extras);

        /*
         * Merge attributes on existing elements building a lookup table for
         * document
         */
        HashMap<String, Node> lookup = new HashMap<String, Node>();
        merge(document, extras, lookup);

        /*
         * Merge new elements. Extras at this point contains only elements that
         * were not found in the document.
         */
        ArrayList<Node> sortedNodes = Collections.list(Collections.enumeration(extras.values()));
        Collections.sort(sortedNodes, new Comparator<Node>() {
            public int compare(Node arg0, Node arg1) {
                int compare = getLevel(arg0) - getLevel(arg1);
                if (compare == 0) {
                    return (arg0).getNodeName().compareTo((arg1).getNodeName());
                }
                return compare;
            }
        });
        String delimiter = System.getProperty("line.separator");
        for (Iterator<Node> iterator = sortedNodes.iterator(); iterator.hasNext();) {
            Node node = iterator.next();
            String name = node.getNodeName();
            if ("arg".equals(name) || "retval".equals(name)) {
                if (!sortedNodes.contains(node.getParentNode()))
                    continue;
            }
            Node parent = lookup.get(getKey(node.getParentNode()));
            Element element = document.createElement(node.getNodeName());
            String text = parent.getChildNodes().getLength() == 0 ? delimiter : "";
            for (int i = 0, level = getLevel(parent) - 1; i < level; i++) {
                text += "  ";
            }
            parent.appendChild(document.createTextNode(text));
            parent.appendChild(element);
            parent.appendChild(document.createTextNode(delimiter));
            NamedNodeMap attributes = node.getAttributes();
            for (int j = 0, length = attributes.getLength(); j < length; j++) {
                Node attr = (Node) attributes.item(j);
                element.setAttribute(attr.getNodeName(), attr.getNodeValue());
            }
            lookup.put(getKey(element), element);
        }
    }

    public void generate(ProgressMonitor progress) throws UsageException {
        if (progress != null) {
            progress.setTotals(3);
            progress.setMessage("extra attributes...");
        }
        generateExtraAttributes();
        if (progress != null) {
            progress.step();
            progress.setMessage(mainClassName);
        }
        generateMainClass();
        if (progress != null) {
            progress.step();
            progress.setMessage("classes...");
        }
        generateClasses();
        if (progress != null) {
            progress.step();
            progress.setMessage("Done.");
        }
    }


    String fixDelimiter(String str) {
        if (delimiter.equals("\n"))
            return str;
        int index = 0, length = str.length();
        StringBuffer buffer = new StringBuffer();
        while (index != -1) {
            int start = index;
            index = str.indexOf('\n', start);
            if (index == -1) {
                buffer.append(str.substring(start, length));
            } else {
                buffer.append(str.substring(start, index));
                buffer.append(delimiter);
                index++;
            }
        }
        return buffer.toString();
    }

    void generateMethods(String className, ArrayList<Node> methods) {
        for (Node method : methods) {
            NamedNodeMap mthAttributes = method.getAttributes();
            String sel = mthAttributes.getNamedItem("selector").getNodeValue();
            out("public ");
            boolean isStatic = isStatic(method);
            if (isStatic)
                out("static ");
            Node returnNode = getReturnNode(method.getChildNodes());
            if (getType(returnNode).equals("void"))
                returnNode = null;
            String returnType = "", returnType64 = "";
            if (returnNode != null) {
                String type = returnType = getJavaType(returnNode), type64 = returnType64 = getJavaType64(returnNode);
                out(type);
                if (!type.equals(type64)) {
                    out(" /*");
                    out(type64);
                    out("*/");
                }
                out(" ");
            } else {
                out("void ");
            }
            String methodName = sel;
            if (isUnique(method, methods)) {
                int index = methodName.indexOf(":");
                if (index != -1)
                    methodName = methodName.substring(0, index);
            } else {
                // TODO improve this selector
                methodName = methodName.replaceAll(":", "_");
                if (isStatic)
                    methodName = "static_" + methodName;
            }
            out(methodName);
            out("(");
            NodeList params = method.getChildNodes();
            boolean first = true;
            for (int k = 0; k < params.getLength(); k++) {
                Node param = params.item(k);
                if ("arg".equals(param.getNodeName())) {
                    NamedNodeMap paramAttributes = param.getAttributes();
                    if (!first)
                        out(", ");
                    String type = getJavaType(param), type64 = getJavaType64(param);
                    out(type);
                    if (!type.equals(type64)) {
                        out(" /*");
                        out(type64);
                        out("*/");
                    }
                    first = false;
                    out(" ");
                    String paramName = paramAttributes.getNamedItem("name").getNodeValue();
                    if (paramName.length() == 0)
                        paramName = "arg" + paramAttributes.getNamedItem("index").getNodeValue();
                    if (paramName.equals("boolean"))
                        paramName = "b";
                    out(paramName);
                }
            }
            out(") {");
            outln();
            if (returnNode != null && isStruct(returnNode)) {
                out("\t");
                out(returnType);
                out(" result = new ");
                out(returnType);
                out("();");
                outln();
                out("\tOS.objc_msgSend_stret(result, ");
            } else if (returnNode != null && isBoolean(returnNode)) {
                out("\treturn ");
                out("OS.objc_msgSend_bool(");
            } else if (returnNode != null && isFloatingPoint(returnNode)) {
                out("\treturn ");
                if (returnType.equals("float"))
                    out("(float)");
                out("OS.objc_msgSend_fpret(");
            } else if (returnNode != null && isObject(returnNode)) {
                out("\tint /*long*/ result = OS.objc_msgSend(");
            } else {
                if (returnNode != null) {
                    out("\treturn ");
                    if ((returnType.equals("int") && returnType64.equals("int")) || !returnType.equals("int")) {
                        out("(");
                        out(returnType);
                        out(")");
                    }
                    if (returnType.equals("int") && returnType64.equals("int")) {
                        out("/*64*/");
                    }
                } else {
                    out("\t");
                }
                out("OS.objc_msgSend(");
            }
            if (isStatic) {
                out("OS.class_");
                out(className);
            } else {
                out("this.id");
            }
            out(", OS.");
            out(getSelConst(sel));
            first = false;
            for (int k = 0; k < params.getLength(); k++) {
                Node param = params.item(k);
                if ("arg".equals(param.getNodeName())) {
                    NamedNodeMap paramAttributes = param.getAttributes();
                    if (!first)
                        out(", ");
                    first = false;
                    String paramName = paramAttributes.getNamedItem("name").getNodeValue();
                    if (paramName.length() == 0)
                        paramName = "arg" + paramAttributes.getNamedItem("index").getNodeValue();
                    if (paramName.equals("boolean"))
                        paramName = "b";
                    if (isObject(param)) {
                        out(paramName);
                        out(" != null ? ");
                        out(paramName);
                        out(".id : 0");
                    } else {
                        out(paramName);
                    }
                }
            }
            out(")");
            out(";");
            outln();
            if (returnNode != null && isObject(returnNode)) {
                if (!isStatic && returnType.equals(className)) {
                    out("\treturn result == this.id ? this : (result != 0 ? new ");
                    out(returnType);
                    out("(result) : null);");
                } else {
                    out("\treturn result != 0 ? new ");
                    NamedNodeMap attributes = returnNode.getAttributes();
                    Node hawtjni_alloc = attributes.getNamedItem("hawtjni_alloc");
                    if (hawtjni_alloc != null && hawtjni_alloc.getNodeValue().equals("true")) {
                        out(className);
                    } else {
                        out(returnType);
                    }
                    out("(result) : null;");
                }
                outln();
            } else if (returnNode != null && isStruct(returnNode)) {
                out("\treturn result;");
                outln();
            }
            out("}");
            outln();
            outln();
        }
    }

    void generateExtraMethods(String className) {
        /* Empty constructor */
        out("public ");
        out(className);
        out("() {");
        outln();
        out("\tsuper();");
        outln();
        out("}");
        outln();
        outln();
        /* pointer constructor */
        out("public ");
        out(className);
        out("(int /*long*/ id) {");
        outln();
        out("\tsuper(id);");
        outln();
        out("}");
        outln();
        outln();
        /* object constructor */
        out("public ");
        out(className);
        out("(id id) {");
        outln();
        out("\tsuper(id);");
        outln();
        out("}");
        outln();
        outln();
        /* NSObject helpers */
        if (className.equals("NSObject")) {
            out("public NSObject alloc() {");
            outln();
            out("\tthis.id = OS.objc_msgSend(objc_getClass(), OS.sel_alloc);");
            outln();
            out("\treturn this;");
            outln();
            out("}");
            outln();
            outln();
        }
        /* NSString helpers */
        if (className.equals("NSString")) {
            /* Get java string */
            out("public String getString() {");
            outln();
            out("\tchar[] buffer = new char[(int)/*64*/length()];");
            outln();
            out("\tgetCharacters(buffer);");
            outln();
            out("\treturn new String(buffer);");
            outln();
            out("}");
            outln();
            outln();
            /* create NSString */
            out("public NSString initWithString(String str) {");
            outln();
            out("\tchar[] buffer = new char[str.length()];");
            outln();
            out("\tstr.getChars(0, buffer.length, buffer, 0);");
            outln();
            out("\treturn initWithCharacters(buffer, buffer.length);");
            outln();
            out("}");
            outln();
            outln();
            out("public static NSString stringWith(String str) {");
            outln();
            out("\tchar[] buffer = new char[str.length()];");
            outln();
            out("\tstr.getChars(0, buffer.length, buffer, 0);");
            outln();
            out("\treturn stringWithCharacters(buffer, buffer.length);");
            outln();
            out("}");
            outln();
            outln();
        }
    }

    static class NodeEntry {
        private final Node parent;
        private final ArrayList<Node> children;

        public NodeEntry(Node parent, ArrayList<Node> children) {
            this.parent = parent;
            this.children = children;
        }
    }
    
    TreeMap<String, NodeEntry> getGeneratedClasses() {
        TreeMap<String, NodeEntry> classes = new TreeMap<String, NodeEntry>();
        for (int x = 0; x < xmls.length; x++) {
            Document document = documents[x];
            if (document == null)
                continue;
            NodeList list = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if ("class".equals(node.getNodeName()) && getGen(node)) {
                    ArrayList<Node> methods;
                    String name = node.getAttributes().getNamedItem("name").getNodeValue();
                    NodeEntry clazz = classes.get(name);
                    if (clazz == null) {
                        methods = new ArrayList<Node>();
                        classes.put(name, new NodeEntry(node, methods));
                    } else {
                        methods = clazz.children;
                    }
                    NodeList methodList = node.getChildNodes();
                    for (int j = 0; j < methodList.getLength(); j++) {
                        Node method = methodList.item(j);
                        if ("method".equals(method.getNodeName()) && getGen(method)) {
                            methods.add(method);
                        }
                    }
                }
            }
        }
        return classes;
    }

    void copyClassMethodsDown(final Map<String, NodeEntry> classes) {
        ArrayList<NodeEntry> sortedClasses = Collections.list(Collections.enumeration(classes.values()));
        Collections.sort(sortedClasses, new Comparator<NodeEntry>() {
            int getHierarchyLevel(Node node) {
                String superclass = getSuperclassName(node);
                int level = 0;
                while (!superclass.equals("id")) {
                    level++;
                    superclass = getSuperclassName(classes.get(superclass).parent);
                }
                return level;
            }

            public int compare(NodeEntry arg0, NodeEntry arg1) {
                return getHierarchyLevel(arg0.parent) - getHierarchyLevel(arg1.parent);
            }
        });
        for (NodeEntry clazz : sortedClasses) {
            Node node = (Node) clazz.parent;
            ArrayList<Node> methods = (ArrayList<Node>) clazz.children;
            NodeEntry superclass = classes.get(getSuperclassName(node));
            if (superclass != null) {
                for (Node method : superclass.children) {
                    if (isStatic(method)) {
                        methods.add(method);
                    }
                }
            }
        }
    }

    String getSuperclassName(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node superclass = attributes.getNamedItem("hawtjni_superclass");
        if (superclass != null) {
            return superclass.getNodeValue();
        } else {
            Node name = attributes.getNamedItem("name");
            if (name.getNodeValue().equals("NSObject")) {
                return "id";
            } else {
                return "NSObject";
            }
        }
    }

    void generateClasses() {
        TreeMap<String, NodeEntry> classes = getGeneratedClasses();
        copyClassMethodsDown(classes);

        Set<String> classNames = classes.keySet();
        for (Iterator<String> iterator = classNames.iterator(); iterator.hasNext();) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            this.out = new PrintStream(out);

//            out(fixDelimiter(metaData.getCopyright()));

            String className = iterator.next();
            NodeEntry clazz = classes.get(className);
            Node node = clazz.parent;
            ArrayList<Node> methods = clazz.children;
            out("package ");
            String packageName = getPackageName(mainClassName);
            out(packageName);
            out(";");
            outln();
            outln();
            out("public class ");
            out(className);
            out(" extends ");
            out(getSuperclassName(node));
            out(" {");
            outln();
            outln();
            generateExtraMethods(className);
            generateMethods(className, methods);
            out("}");
            outln();

            String fileName = outputDir + packageName.replace('.', '/') + "/" + className + ".java";
            try {
                out.flush();
                if (out.size() > 0) {
                    FileSupport.write(out.toByteArray(), new File(fileName));
                }
            } catch (Exception e) {
                System.out.println("Problem");
                e.printStackTrace(System.out);
            }
            out = null;
        }
    }

    void generateExtraAttributes() {
        Document[] documents = getDocuments();
        for (int x = 0; x < xmls.length; x++) {
            Document document = documents[x];
            if (document == null || !getGen(document.getDocumentElement()))
                continue;
            saveExtraAttributes(xmls[x], document);
        }
    }

    void generateMainClass() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        this.out = new PrintStream(out);

        String header = "", footer = "";
        String fileName = outputDir + mainClassName.replace('.', '/') + ".java";
        FileInputStream is = null;
        try {
            InputStreamReader input = new InputStreamReader(new BufferedInputStream(is = new FileInputStream(fileName)));
            StringBuffer str = new StringBuffer();
            char[] buffer = new char[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                str.append(buffer, 0, read);
            }
            String section = "/** This section is auto generated */";
            int start = str.indexOf(section) + section.length();
            int end = str.indexOf(section, start);
            header = str.substring(0, start);
            footer = str.substring(end);
        } catch (IOException e) {
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (IOException e) {
            }
        }

        out(header);
        outln();
        outln();

        out("/** Custom callbacks */");
        outln();
        generateCustomCallbacks();
        outln();
        out("/** Classes */");
        outln();
        generateClassesConst();
        outln();
        out("/** Protocols */");
        outln();
        generateProtocolsConst();
        outln();
        out("/** Selectors */");
        outln();
        generateSelectorsConst();
        outln();
        out("/** Constants */");
        outln();
        generateEnums();
        outln();
        out("/** Globals */");
        outln();
        generateConstants();
        outln();
        out("/** Functions */");
        outln();
        outln();
        generateFunctions();
        outln();
        out("/** Super Sends */");
        outln();
        generateSends(true);
        outln();
        out("/** Sends */");
        outln();
        generateSends(false);
        outln();
        generateStructNatives();

        outln();
        out(footer);
        try {
            out.flush();
            if (out.size() > 0) {
                FileSupport.write(out.toByteArray(), new File(fileName));
                
            }
        } catch (Exception e) {
            System.out.println("Problem");
            e.printStackTrace(System.out);
        }
    }

    public Document[] getDocuments() {
        if (documents == null) {
            String[] xmls = getXmls();
            documents = new Document[xmls.length];
            for (int i = 0; i < xmls.length; i++) {
                String xmlPath = xmls[i];
                Document document = documents[i] = getDocument(xmlPath);
                if (document == null)
                    continue;
                if (mainClassName != null && outputDir != null) {
                    String packageName = getPackageName(mainClassName);
                    String extrasPath = outputDir + packageName.replace('.', '/') + "/" + getFileName(xmlPath) + ".extras";
                    merge(document, getDocument(extrasPath));
                }
            }
        }
        return documents;
    }

    public String[] getXmls() {
        if (xmls == null || xmls.length == 0) {
            ArrayList<String> array = new ArrayList<String>();
            list(new File("/System/Library/Frameworks"), array);
            list(new File("/System/Library/Frameworks/CoreServices.framework/Frameworks"), array);
            list(new File("/System/Library/Frameworks/ApplicationServices.framework/Frameworks"), array);
            Collections.sort(array, new Comparator<String>() {
                public int compare(String o1, String o2) {
                    return new File(o1).getName().compareTo(new File(o2).getName());
                }
            });
            xmls = array.toArray(new String[array.size()]);
        }
        return xmls;
    }

    void saveExtraAttributes(String xmlPath, Document document) {
        try {
            String packageName = getPackageName(mainClassName);
            String fileName = outputDir + packageName.replace('.', '/') + "/" + getFileName(xmlPath) + ".extras";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DOMWriter writer = new DOMWriter(new PrintStream(out));
            String[] names = getIDAttributeNames();
            String[] filter = new String[names.length + 2];
            filter[0] = "class_method";
            filter[1] = "hawtjni_.*";
            System.arraycopy(names, 0, filter, 2, names.length);
            writer.setAttributeFilter(filter);
            writer.setNodeFilter("hawtjni_");
            writer.print(document);
            if (out.size() > 0) {
                FileSupport.write(out.toByteArray(), new File(fileName));
            }
        } catch (Exception e) {
            System.out.println("Problem");
            e.printStackTrace(System.out);
        }
    }

    public void setOutputDir(String dir) {
        if (dir != null) {
            if (!dir.endsWith("\\") && !dir.endsWith("/")) {
                dir += "/";
            }
        }
        this.outputDir = dir;
    }

    public void setXmls(String[] xmls) {
        this.xmls = xmls;
        this.documents = null;
    }

    public void setMainClass(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    Document getDocument(String xmlPath) {
        try {
            InputStream is = null;
            if (xmlPath.indexOf(File.separatorChar) == -1)
                is = getClass().getResourceAsStream(xmlPath);
            if (is == null)
                is = new BufferedInputStream(new FileInputStream(xmlPath));
            if (is != null)
                return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(is));
        } catch (Exception e) {
            // e.printStackTrace();
        }
        return null;
    }

    public String[] getExtraAttributeNames(Node node) {
        String name = node.getNodeName();
        if (name.equals("method")) {
            return new String[] { "hawtjni_gen_super_msgSend", "hawtjni_gen_custom_callback" };
        } else if (name.equals("function")) {
            NamedNodeMap attribs = node.getAttributes();
            if (attribs != null && attribs.getNamedItem("variadic") != null) {
                return new String[] { "hawtjni_variadic_count", "hawtjni_variadic_java_types" };
            }
        } else if (name.equals("class")) {
            return new String[] { "hawtjni_superclass" };
        } else if (name.equals("retval")) {
            return new String[] { "hawtjni_java_type", "hawtjni_java_type64", "hawtjni_alloc" };
        } else if (name.equals("arg")) {
            return new String[] { "hawtjni_java_type", "hawtjni_java_type64" };
        }
        return new String[0];
    }

    public String getFileName(String xmlPath) {
        File file = new File(xmlPath);
        return file.getName();
    }

    String getKey(Node node) {
        StringBuffer buffer = new StringBuffer();
        while (node != null) {
            if (buffer.length() > 0)
                buffer.append("_");
            String name = node.getNodeName();
            StringBuffer key = new StringBuffer(name);
            Node nameAttrib = getIDAttribute(node);
            if (nameAttrib != null) {
                key.append("-");
                key.append(nameAttrib.getNodeValue());
            }
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                boolean isStatic = attributes.getNamedItem("class_method") != null;
                if (isStatic)
                    key.append("-static");
            }
            buffer.append(key.reverse());
            node = node.getParentNode();
        }
        buffer.reverse();
        return buffer.toString();
    }

    public Node getIDAttribute(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        if (attributes == null)
            return null;
        String[] names = getIDAttributeNames();
        for (int i = 0; i < names.length; i++) {
            Node nameAttrib = attributes.getNamedItem(names[i]);
            if (nameAttrib != null)
                return nameAttrib;
        }
        return null;
    }

    public String[] getIDAttributeNames() {
        return new String[] { "name", "selector", "path", };
    }

    void merge(Node node, HashMap<String, Node> extras, HashMap<String, Node> docLookup) {
        NodeList list = node.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node childNode = list.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                String key = getKey(childNode);
                if (docLookup != null && docLookup.get(key) == null) {
                    docLookup.put(key, childNode);
                }
                Node extra = extras.remove(key);
                if (extra != null) {
                    NamedNodeMap attributes = extra.getAttributes();
                    for (int j = 0, length = attributes.getLength(); j < length; j++) {
                        Node attr = (Node) attributes.item(j);
                        String name = attr.getNodeName();
                        if (name.startsWith("hawtjni_")) {
                            ((Element) childNode).setAttribute(name, attr.getNodeValue());
                        }
                    }
                }
            }
            merge(childNode, extras, docLookup);
        }
    }

    void out(String str) {
        PrintStream out = this.out;
        if (out == null)
            out = System.out;
        out.print(str);
    }

    void outln() {
        PrintStream out = this.out;
        if (out == null)
            out = System.out;
        out.println();
    }

    void generateConstants() {
        for (int x = 0; x < xmls.length; x++) {
            Document document = documents[x];
            if (document == null)
                continue;
            NodeList list = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if ("constant".equals(node.getNodeName())) {
                    if (getGen(node)) {
                        NamedNodeMap attributes = node.getAttributes();
                        String constName = attributes.getNamedItem("name").getNodeValue();
                        out("/** @method flags=const */");
                        outln();
                        out("public static final native ");
                        String type = getType(node), type64 = getType64(node);
                        out(type);
                        if (!type.equals(type64)) {
                            out(" /*");
                            out(type64);
                            out("*/");
                        }
                        out(" ");
                        out(constName);
                        out("();");
                        outln();
                        if (attributes.getNamedItem("declared_type").getNodeValue().equals("NSString*")) {
                            out("public static final NSString ");
                            out(constName);
                            out(" = new NSString(");
                            out(constName);
                            out("());");
                            outln();
                        }
                    }
                }
            }
        }
    }

    void generateEnums() {
        for (int x = 0; x < xmls.length; x++) {
            Document document = documents[x];
            if (document == null)
                continue;
            NodeList list = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if ("enum".equals(node.getNodeName())) {
                    if (getGen(node)) {
                        NamedNodeMap attributes = node.getAttributes();
                        Node valueNode = attributes.getNamedItem("value");
                        if (valueNode != null) {
                            String value = valueNode.getNodeValue();
                            out("public static final ");
                            boolean isLong = false;
                            if (value.indexOf('.') != -1) {
                                out("double ");
                            } else {
                                try {
                                    Integer.parseInt(value);
                                    out("int ");
                                } catch (NumberFormatException e) {
                                    isLong = true;
                                    out("long ");
                                }
                            }
                            out(attributes.getNamedItem("name").getNodeValue());
                            out(" = ");
                            out(value);
                            if (isLong && !value.endsWith("L"))
                                out("L");
                            out(";");
                            outln();
                        }
                    }
                }
            }
        }
    }

    boolean getGen(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        if (attributes == null)
            return false;
        Node gen = attributes.getNamedItem("hawtjni_gen");
        return gen != null && !gen.getNodeValue().equals("false");
    }

    boolean getGenSuper(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        if (attributes == null)
            return false;
        Node gen = attributes.getNamedItem("hawtjni_gen_super_msgSend");
        return gen != null && !gen.getNodeValue().equals("false");
    }

    boolean getGenCallback(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        if (attributes == null)
            return false;
        Node gen = attributes.getNamedItem("hawtjni_gen_custom_callback");
        return gen != null && !gen.getNodeValue().equals("false");
    }

    boolean isStatic(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node isStatic = attributes.getNamedItem("class_method");
        return isStatic != null && isStatic.getNodeValue().equals("true");
    }

    boolean isStruct(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        String code = attributes.getNamedItem("type").getNodeValue();
        return code.startsWith("{");
    }

    boolean isFloatingPoint(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        String code = attributes.getNamedItem("type").getNodeValue();
        return code.equals("f") || code.equals("d");
    }

    boolean isObject(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        String code = attributes.getNamedItem("type").getNodeValue();
        return code.equals("@");
    }

    boolean isBoolean(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        String code = attributes.getNamedItem("type").getNodeValue();
        return code.equals("B");
    }

    void buildLookup(Node node, HashMap<String, Node> table) {
        NodeList list = node.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node childNode = list.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                String key = getKey(childNode);
                if (table.get(key) == null)
                    table.put(key, childNode);
                buildLookup(childNode, table);
            }
        }
    }

    boolean isUnique(Node method, ArrayList<Node> methods) {
        String methodName = method.getAttributes().getNamedItem("selector").getNodeValue();
        String signature = "";
        NodeList params = method.getChildNodes();
        for (int k = 0; k < params.getLength(); k++) {
            Node param = params.item(k);
            if ("arg".equals(param.getNodeName())) {
                signature += getJavaType(param);
            }
        }
        int index = methodName.indexOf(":");
        if (index != -1)
            methodName = methodName.substring(0, index);
        for (Node node : methods) {
            NamedNodeMap attributes = node.getAttributes();
            Node otherSel = null;
            if (attributes != null)
                otherSel = attributes.getNamedItem("selector");
            if (node != method && otherSel != null) {
                String otherName = otherSel.getNodeValue();
                index = otherName.indexOf(":");
                if (index != -1)
                    otherName = otherName.substring(0, index);
                if (methodName.equals(otherName)) {
                    NodeList otherParams = node.getChildNodes();
                    String otherSignature = "";
                    for (int k = 0; k < otherParams.getLength(); k++) {
                        Node param = otherParams.item(k);
                        if ("arg".equals(param.getNodeName())) {
                            otherSignature += getJavaType(param);
                        }
                    }
                    if (signature.equals(otherSignature)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    void generateSelectorsConst() {
        TreeSet<String> set = new TreeSet<String>();
        for (int x = 0; x < xmls.length; x++) {
            Document document = documents[x];
            if (document == null)
                continue;
            NodeList list = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if ("class".equals(node.getNodeName()) || "informal_protocol".equals(node.getNodeName())) {
                    if (getGen(node)) {
                        NodeList methods = node.getChildNodes();
                        for (int j = 0; j < methods.getLength(); j++) {
                            Node method = methods.item(j);
                            if (getGen(method)) {
                                NamedNodeMap mthAttributes = method.getAttributes();
                                String sel = mthAttributes.getNamedItem("selector").getNodeValue();
                                set.add(sel);
                            }
                        }
                    }
                }
            }
        }
        set.add("alloc");
        for (Iterator<String> iterator = set.iterator(); iterator.hasNext();) {
            String sel = iterator.next();
            String selConst = getSelConst(sel);
            out("public static final int /*long*/ ");
            out(selConst);
            out(" = ");
            out("sel_registerName(\"");
            out(sel);
            out("\");");
            outln();
        }
    }

    void generateStructNatives() {
        TreeSet<String> set = new TreeSet<String>();
        for (int x = 0; x < xmls.length; x++) {
            Document document = documents[x];
            if (document == null)
                continue;
            NodeList list = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if ("struct".equals(node.getNodeName()) && getGen(node)) {
                    set.add(getIDAttribute(node).getNodeValue());
                }
            }
        }
        out("/** Sizeof natives */");
        outln();
        for (Iterator<String> iterator = set.iterator(); iterator.hasNext();) {
            String struct = iterator.next();
            out("public static final native int ");
            out(struct);
            out("_sizeof();");
            outln();
        }
        outln();
        out("/** Memmove natives */");
        outln();
        outln();
        for (Iterator<String> iterator = set.iterator(); iterator.hasNext();) {
            String struct = iterator.next();
            out("/**");
            outln();
            out(" * @param dest cast=(void *),flags=no_in critical");
            outln();
            out(" * @param src cast=(void *),flags=critical");
            // out(" * @param src cast=(void *),flags=no_out critical");
            outln();
            out(" */");
            outln();
            out("public static final native void memmove(");
            out("int /*long*/ dest, ");
            out(struct);
            out(" src, int /*long*/ size);");
            outln();
            out("/**");
            outln();
            out(" * @param dest cast=(void *),flags=no_in critical");
            outln();
            out(" * @param src cast=(void *),flags=critical");
            // out(" * @param src cast=(void *),flags=no_out critical");
            outln();
            out(" */");
            outln();
            out("public static final native void memmove(");
            out(struct);
            out(" dest, int /*long*/ src, int /*long*/ size);");
            outln();
        }
    }

    String buildSend(Node method, boolean tags, boolean only64, boolean superCall) {
        Node returnNode = getReturnNode(method.getChildNodes());
        StringBuffer buffer = new StringBuffer();
        buffer.append("public static final native ");
        if (returnNode != null && isStruct(returnNode)) {
            buffer.append("void ");
            buffer.append(superCall ? "objc_msgSendSuper_stret" : "objc_msgSend_stret");
            buffer.append("(");
            buffer.append(getJavaType(returnNode));
            buffer.append(" result, ");
        } else if (returnNode != null && isFloatingPoint(returnNode)) {
            buffer.append("double ");
            buffer.append(superCall ? "objc_msgSendSuper_fpret" : "objc_msgSend_fpret");
            buffer.append("(");
        } else if (returnNode != null && isBoolean(returnNode)) {
            buffer.append("boolean ");
            buffer.append(superCall ? "objc_msgSendSuper_bool" : "objc_msgSend_bool");
            buffer.append("(");
        } else {
            if (only64) {
                buffer.append("long");
            } else {
                if (tags) {
                    buffer.append("int /*long*/");
                } else {
                    buffer.append("int");
                }
            }
            buffer.append(" ");
            buffer.append(superCall ? "objc_msgSendSuper" : "objc_msgSend");
            buffer.append("(");
        }
        if (superCall) {
            if (only64) {
                buffer.append("objc_super superId, long sel");
            } else {
                if (tags) {
                    buffer.append("objc_super superId, int /*long*/ sel");
                } else {
                    buffer.append("objc_super superId, int sel");
                }
            }
        } else {
            if (only64) {
                buffer.append("long id, long sel");
            } else {
                if (tags) {
                    buffer.append("int /*long*/ id, int /*long*/ sel");
                } else {
                    buffer.append("int id, int sel");
                }
            }
        }
        NodeList params = method.getChildNodes();
        boolean first = false;
        int count = 0;
        for (int k = 0; k < params.getLength(); k++) {
            Node param = params.item(k);
            if ("arg".equals(param.getNodeName())) {
                if (!first)
                    buffer.append(", ");
                if (isStruct(param)) {
                    buffer.append(getJavaType(param));
                } else {
                    String type = getType(param), type64 = getType64(param);
                    buffer.append(only64 ? type64 : type);
                    if (!only64 && tags && !type.equals(type64)) {
                        buffer.append(" /*");
                        buffer.append(type64);
                        buffer.append("*/");
                    }
                }
                first = false;
                buffer.append(" arg");
                buffer.append(String.valueOf(count++));
            }
        }
        buffer.append(");");
        return buffer.toString();
    }

    String getCType(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        return attributes.getNamedItem("declared_type").getNodeValue();
    }

    Node findNSObjectMethod(Node method) {
        NamedNodeMap methodAttributes = method.getAttributes();
        String selector = methodAttributes.getNamedItem("selector").getNodeValue();
        NodeList list = method.getParentNode().getParentNode().getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node cls = list.item(i);
            if ("class".equals(cls.getNodeName())) {
                NamedNodeMap classAttributes = cls.getAttributes();
                if ("NSObject".equals(classAttributes.getNamedItem("name").getNodeValue())) {
                    NodeList methods = cls.getChildNodes();
                    for (int j = 0; j < methods.getLength(); j++) {
                        Node mth = methods.item(j);
                        if ("method".equals(mth.getNodeName())) {
                            NamedNodeMap mthAttributes = mth.getAttributes();
                            if (selector.equals(mthAttributes.getNamedItem("selector").getNodeValue())) {
                                return mth;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    void generateCustomCallbacks() {
        TreeMap<String, Node> set = new TreeMap<String, Node>();
        for (int x = 0; x < xmls.length; x++) {
            Document document = documents[x];
            if (document == null)
                continue;
            NodeList list = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if (("class".equals(node.getNodeName()) || "informal_protocol".equals(node.getNodeName())) && getGen(node)) {
                    NodeList methods = node.getChildNodes();
                    for (int j = 0; j < methods.getLength(); j++) {
                        Node method = methods.item(j);
                        if ("method".equals(method.getNodeName()) && getGen(method) && getGenCallback(method)) {
                            NamedNodeMap mthAttributes = method.getAttributes();
                            String sel = mthAttributes.getNamedItem("selector").getNodeValue();
                            set.put(sel, method);
                        }
                    }
                }
            }
        }
        for (Iterator<String> iterator = set.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            Node method = set.get(key);
            if ("informal_protocol".equals(method.getParentNode().getNodeName())) {
                method = findNSObjectMethod(method);
                if (method == null)
                    continue;
            }
            String nativeMth = key.replaceAll(":", "_");
            out("/** @method callback_types=");
            Node returnNode = getReturnNode(method.getChildNodes());
            out(returnNode == null ? "void" : getCType(returnNode));
            out(";id;SEL;");
            NodeList params = method.getChildNodes();
            for (int k = 0; k < params.getLength(); k++) {
                Node param = params.item(k);
                if ("arg".equals(param.getNodeName())) {
                    out(getCType(param));
                    out(";");
                }
            }
            out(",callback_flags=");
            out(returnNode != null && isStruct(returnNode) ? "struct" : "none");
            out(";none;none;");
            for (int k = 0; k < params.getLength(); k++) {
                Node param = params.item(k);
                if ("arg".equals(param.getNodeName())) {
                    out(isStruct(param) ? "struct" : "none");
                    out(";");
                }
            }
            out(" */");
            outln();
            out("public static final native int /*long*/ CALLBACK_");
            out(nativeMth);
            out("(int /*long*/ func);");
            outln();
        }
    }

    void generateSends(boolean superCall) {
        TreeMap<String, Node> set = new TreeMap<String, Node>();
        TreeMap<String, Node> set64 = new TreeMap<String, Node>();
        for (int x = 0; x < xmls.length; x++) {
            Document document = documents[x];
            if (document == null)
                continue;
            NodeList list = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if ("class".equals(node.getNodeName()) && getGen(node)) {
                    NodeList methods = node.getChildNodes();
                    for (int j = 0; j < methods.getLength(); j++) {
                        Node method = methods.item(j);
                        if ("method".equals(method.getNodeName()) && getGen(method) && (!superCall || getGenSuper(method))) {
                            String code = buildSend(method, false, false, superCall);
                            String code64 = buildSend(method, false, true, superCall);
                            if (set.get(code) == null) {
                                set.put(code, method);
                            }
                            if (set64.get(code64) == null) {
                                set64.put(code64, method);
                            }
                        }
                    }
                }
            }
        }
        outln();
        TreeMap<String, Node> tagsSet = new TreeMap<String, Node>();
        for (Iterator<String> iterator = set.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            Node method = set.get(key);
            String tagCode = buildSend(method, false, true, superCall);
            if (set64.get(tagCode) != null) {
                tagsSet.put(key, method);
                iterator.remove();
                set64.remove(tagCode);
            }
        }
        TreeMap<String, Node> all = new TreeMap<String, Node>();
        for (Iterator<String> iterator = tagsSet.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            Node method = tagsSet.get(key);
            all.put(buildSend(method, true, false, superCall), method);
        }
        for (Iterator<String> iterator = set.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            all.put(key, set.get(key));
        }
        for (Iterator<String> iterator = set64.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            all.put(key, set64.get(key));
        }
        for (Iterator<String> iterator = all.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            Node method = all.get(key);
            NodeList params = method.getChildNodes();
            ArrayList<String> tags = new ArrayList<String>();
            int count = 0;
            for (int k = 0; k < params.getLength(); k++) {
                Node param = params.item(k);
                if ("arg".equals(param.getNodeName())) {
                    if (isStruct(param)) {
                        tags.add(" * @param arg" + count + " flags=struct");
                    }
                    count++;
                }
            }
            out("/**");
            if (tags.size() > 0) {
                outln();
                out(" *");
            }
            out(" @method flags=cast");
            if (tags.size() > 0)
                outln();
            for (String tag : tags) {
                out(tag);
                outln();
            }
            out(" */");
            outln();
            out(key.toString());
            outln();
        }
    }

    String getSelConst(String sel) {
        return "sel_" + sel.replaceAll(":", "_");
    }

    void generateClassesConst() {
        TreeSet<String> set = new TreeSet<String>();
        for (int x = 0; x < xmls.length; x++) {
            Document document = documents[x];
            if (document == null)
                continue;
            NodeList list = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if ("class".equals(node.getNodeName())) {
                    if (getGen(node)) {
                        NamedNodeMap attributes = node.getAttributes();
                        String name = attributes.getNamedItem("name").getNodeValue();
                        set.add(name);
                    }
                }
            }
        }
        for (Iterator<String> iterator = set.iterator(); iterator.hasNext();) {
            String cls = iterator.next();
            String clsConst = "class_" + cls;
            out("public static final int /*long*/ ");
            out(clsConst);
            out(" = ");
            out("objc_getClass(\"");
            out(cls);
            out("\");");
            outln();
        }
    }

    void generateProtocolsConst() {
        TreeSet<String> set = new TreeSet<String>();
        for (int x = 0; x < xmls.length; x++) {
            Document document = documents[x];
            if (document == null)
                continue;
            NodeList list = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if ("informal_protocol".equals(node.getNodeName())) {
                    if (getGen(node)) {
                        NamedNodeMap attributes = node.getAttributes();
                        String name = attributes.getNamedItem("name").getNodeValue();
                        set.add(name);
                    }
                }
            }
        }
        for (Iterator<String> iterator = set.iterator(); iterator.hasNext();) {
            String cls = iterator.next();
            String clsConst = "protocol_" + cls;
            out("public static final int /*long*/ ");
            out(clsConst);
            out(" = ");
            out("objc_getProtocol(\"");
            out(cls);
            out("\");");
            outln();
        }
    }

    String getPackageName(String className) {
        int dot = mainClassName.lastIndexOf('.');
        if (dot == -1)
            return "";
        return mainClassName.substring(0, dot);
    }

    String getClassName(String className) {
        int dot = mainClassName.lastIndexOf('.');
        if (dot == -1)
            return mainClassName;
        return mainClassName.substring(dot + 1);
    }

    Node getReturnNode(NodeList list) {
        for (int j = 0; j < list.getLength(); j++) {
            Node node = list.item(j);
            if ("retval".equals(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }

    String getType(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node javaType = attributes.getNamedItem("hawtjni_java_type");
        if (javaType != null)
            return javaType.getNodeValue();
        String code = attributes.getNamedItem("type").getNodeValue();
        return getType(code, attributes, false);
    }

    String getType64(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node javaType = attributes.getNamedItem("hawtjni_java_type");
        if (javaType != null) {
            Node javaType64 = attributes.getNamedItem("hawtjni_java_type64");
            return javaType64 != null ? javaType64.getNodeValue() : javaType.getNodeValue();
        }
        Node attrib = attributes.getNamedItem("type");
        String code = attrib.getNodeValue();
        Node attrib64 = attributes.getNamedItem("type64");
        if (attrib64 != null)
            code = attrib64.getNodeValue();
        return getType(code, attributes, true);
    }

    String getType(String code, NamedNodeMap attributes, boolean is64) {
        if (code.equals("c"))
            return "byte";
        if (code.equals("i"))
            return "int";
        if (code.equals("s"))
            return "short";
        if (code.equals("l"))
            return "int";
        if (code.equals("q"))
            return "long";
        if (code.equals("C"))
            return "byte";
        if (code.equals("I"))
            return "int";
        if (code.equals("S"))
            return "short";
        if (code.equals("L"))
            return "int";
        if (code.equals("Q"))
            return "long";
        if (code.equals("f"))
            return "float";
        if (code.equals("d"))
            return "double";
        if (code.equals("B"))
            return "boolean";
        if (code.equals("v"))
            return "void";
        if (code.equals("*"))
            return is64 ? "long" : "int";
        if (code.equals("@"))
            return is64 ? "long" : "int";
        if (code.equals("#"))
            return is64 ? "long" : "int";
        if (code.equals(":"))
            return is64 ? "long" : "int";
        if (code.startsWith("^"))
            return is64 ? "long" : "int";
        if (code.startsWith("{")) {
            return attributes.getNamedItem("declared_type").getNodeValue();
        }
        return "BAD " + code;
    }

    String getJNIType(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        String code = attributes.getNamedItem("type").getNodeValue();
        if (code.equals("c"))
            return "B";
        if (code.equals("i"))
            return "I";
        if (code.equals("s"))
            return "S";
        if (code.equals("l"))
            return "I";
        if (code.equals("q"))
            return "J";
        if (code.equals("C"))
            return "B";
        if (code.equals("I"))
            return "I";
        if (code.equals("S"))
            return "S";
        if (code.equals("L"))
            return "I";
        if (code.equals("Q"))
            return "J";
        if (code.equals("f"))
            return "F";
        if (code.equals("d"))
            return "D";
        if (code.equals("B"))
            return "Z";
        if (code.equals("v"))
            return "V";
        if (code.equals("*"))
            return "I";
        if (code.equals("@"))
            return "I";
        if (code.equals("#"))
            return "I";
        if (code.equals(":"))
            return "I";
        if (code.startsWith("^"))
            return "I";
        if (code.startsWith("["))
            return "BAD " + code;
        if (code.startsWith("{")) {
            return "BAD " + code;
        }
        if (code.startsWith("("))
            return "BAD " + code;
        return "BAD " + code;
    }

    String getJavaType(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node javaType = attributes.getNamedItem("hawtjni_java_type");
        if (javaType != null)
            return javaType.getNodeValue().trim();
        String code = attributes.getNamedItem("type").getNodeValue();
        return getJavaType(code, attributes, false);
    }

    String getJavaType64(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node javaType = attributes.getNamedItem("hawtjni_java_type");
        if (javaType != null) {
            Node javaType64 = attributes.getNamedItem("hawtjni_java_type64");
            return javaType64 != null ? javaType64.getNodeValue() : javaType.getNodeValue();
        }
        Node attrib = attributes.getNamedItem("type");
        String code = attrib.getNodeValue();
        Node attrib64 = attributes.getNamedItem("type64");
        if (attrib64 != null)
            code = attrib64.getNodeValue();
        return getJavaType(code, attributes, true);
    }

    String getJavaType(String code, NamedNodeMap attributes, boolean is64) {
        if (code.equals("c"))
            return "byte";
        if (code.equals("i"))
            return "int";
        if (code.equals("s"))
            return "short";
        if (code.equals("l"))
            return "int";
        if (code.equals("q"))
            return "long";
        if (code.equals("C"))
            return "byte";
        if (code.equals("I"))
            return "int";
        if (code.equals("S"))
            return "short";
        if (code.equals("L"))
            return "int";
        if (code.equals("Q"))
            return "long";
        if (code.equals("f"))
            return "float";
        if (code.equals("d"))
            return "double";
        if (code.equals("B"))
            return "boolean";
        if (code.equals("v"))
            return "void";
        if (code.equals("*"))
            return is64 ? "long" : "int";
        if (code.equals("#"))
            return is64 ? "long" : "int";
        if (code.equals(":"))
            return is64 ? "long" : "int";
        if (code.startsWith("^"))
            return is64 ? "long" : "int";
        if (code.equals("@")) {
            String type = attributes.getNamedItem("declared_type").getNodeValue();
            int index = type.indexOf('*');
            if (index != -1)
                type = type.substring(0, index);
            index = type.indexOf('<');
            if (index != -1)
                type = type.substring(0, index);
            return type.trim();
        }
        if (code.startsWith("{")) {
            return attributes.getNamedItem("declared_type").getNodeValue().trim();
        }
        return "BAD " + code;
    }

    static String[] split(String str, String separator) {
        StringTokenizer tk = new StringTokenizer(str, separator);
        ArrayList<Object> result = new ArrayList<Object>();
        while (tk.hasMoreElements()) {
            result.add(tk.nextElement());
        }
        return result.toArray(new String[result.size()]);
    }

    void generateFunctions() {
        for (int x = 0; x < xmls.length; x++) {
            Document document = documents[x];
            if (document == null)
                continue;
            NodeList list = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if ("function".equals(node.getNodeName())) {
                    if (getGen(node)) {
                        NamedNodeMap attributes = node.getAttributes();
                        String name = attributes.getNamedItem("name").getNodeValue();
                        NodeList params = node.getChildNodes();
                        int count = 0;
                        for (int j = 0; j < params.getLength(); j++) {
                            Node param = params.item(j);
                            if ("arg".equals(param.getNodeName())) {
                                count++;
                            }
                        }
                        if (count > 0) {
                            out("/**");
                            outln();
                        }
                        for (int j = 0; j < params.getLength(); j++) {
                            Node param = params.item(j);
                            if ("arg".equals(param.getNodeName())) {
                                NamedNodeMap paramAttributes = param.getAttributes();
                                out(" * @param ");
                                out(paramAttributes.getNamedItem("name").getNodeValue());
                                if (isStruct(param)) {
                                    out(" flags=struct");
                                } else {
                                    out(" cast=");
                                    Node declaredType = paramAttributes.getNamedItem("declared_type");
                                    String cast = declaredType.getNodeValue();
                                    if (!cast.startsWith("("))
                                        out("(");
                                    out(cast);
                                    if (!cast.endsWith(")"))
                                        out(")");
                                }
                                outln();
                            }
                        }
                        if (count > 0) {
                            out(" */");
                            outln();
                        }
                        out("public static final native ");
                        Node returnNode = getReturnNode(node.getChildNodes());
                        if (returnNode != null) {
                            String type = getType(returnNode), type64 = getType64(returnNode);
                            out(type);
                            if (!type.equals(type64)) {
                                out(" /*");
                                out(type64);
                                out("*/");
                            }
                            out(" ");
                        } else {
                            out("void ");
                        }
                        out(name);
                        out("(");
                        params = node.getChildNodes();
                        boolean first = true;
                        for (int j = 0; j < params.getLength(); j++) {
                            Node param = params.item(j);
                            if ("arg".equals(param.getNodeName())) {
                                NamedNodeMap paramAttributes = param.getAttributes();
                                if (!first)
                                    out(", ");
                                first = false;
                                String type = getType(param), type64 = getType64(param);
                                out(type);
                                if (!type.equals(type64)) {
                                    out(" /*");
                                    out(type64);
                                    out("*/");
                                }
                                out(" ");
                                out(paramAttributes.getNamedItem("name").getNodeValue());
                            }
                        }
                        generateVariadics(node);
                        out(");");
                        outln();
                    }
                }
            }
        }
    }

    void generateVariadics(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node variadicCount = attributes.getNamedItem("hawtjni_variadic_count");
        if (variadicCount != null) {
            Node variadicTypes = attributes.getNamedItem("hawtjni_variadic_java_types");
            String[] types = null;
            if (variadicTypes != null) {
                types = split(variadicTypes.getNodeValue(), ",");
            }
            int varCount = 0;
            try {
                varCount = Integer.parseInt(variadicCount.getNodeValue());
            } catch (NumberFormatException e) {
            }
            for (int j = 0; j < varCount; j++) {
                out(", ");
                if (types != null && types.length > j && !types[j].equals("*")) {
                    out(types[j]);
                } else if (types != null && types[types.length - 1].equals("*")) {
                    out(types[types.length - 2]);
                } else {
                    out("int /*long*/");
                }
                out(" varArg");
                out("" + j);
            }
        }
    }

    public static void main(String[] args) {
        try {
            MacGenerator gen = new MacGenerator();
            gen.setXmls(args);
            gen.setOutputDir("../org.eclipse.hawtjni/Eclipse SWT PI/cocoa/");
            gen.setMainClass("org.eclipse.hawtjni.internal.cocoa.OS");
            gen.generate(null);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
