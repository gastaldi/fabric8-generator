/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.forge.generator.che;

import io.fabric8.forge.addon.utils.CommandHelpers;
import io.fabric8.utils.DomHelper;
import io.fabric8.utils.Files;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.ui.context.UIContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 */
public class CheStackDetector {
    private static final transient Logger LOG = LoggerFactory.getLogger(CheStackDetector.class);

    protected static Map<String, CheStack> mavenPluginMap = new LinkedHashMap<>();
    protected static Map<String, CheStack> mavenDependencyGroupMap = new LinkedHashMap<>();

    static {
        mavenPluginMap.put("org.springframework.boot:spring-boot-maven-plugin", CheStack.SpringBoot);
        mavenPluginMap.put("org.wildfly.swarm:wildfly-swarm-plugin", CheStack.WildFlySwarm);
        mavenPluginMap.put("io.fabric8:vertx-maven-plugin", CheStack.Vertx);

        mavenDependencyGroupMap.put("org.springframework.boot", CheStack.SpringBoot);
        mavenDependencyGroupMap.put("io.vertx", CheStack.Vertx);
        mavenDependencyGroupMap.put("org.wildfly.swarm", CheStack.WildFlySwarm);
    }

    /**
     * Lets detect the default stack to use for the newly created project
     */
    public static CheStack detectCheStack(UIContext context, org.jboss.forge.addon.projects.Project project) {
        if (project != null) {
            if (hasFile(context, project, "package.json")) {
                return CheStack.NodeJS;
            }
            File pomFile = CommandHelpers.getProjectContextFile(context, project, "pom.xml");
            if (Files.isFile(pomFile)) {
                Document doc;
                try {
                    doc = parseXmlFile(pomFile);
                } catch (Exception e) {
                    LOG.debug("Failed to parse " + pomFile + " with: " + e, e);
                    return CheStack.JavaCentOS;
                }
                if (doc != null) {
                    return detectStackFromPomXml(doc);
                }
            }
        }
        // TODO assume Java?
        return CheStack.JavaCentOS;
    }

    protected static Document parseXmlFile(File pomFile) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return documentBuilder.parse(pomFile);
    }

    private static CheStack detectStackFromPomXml(Document doc) {
        // lets find the maven plugins first
        NodeList plugins = doc.getElementsByTagName("plugins");
        if (plugins != null) {
            for (int i = 0, size = plugins.getLength(); i < size; i++) {
                Node item = plugins.item(i);
                if (item instanceof Element) {
                    Element element = (Element) item;
                    String groupId = firstElementText(element, "groupId");
                    String artifactId = firstElementText(element, "groupId");
                    if (Strings.isNotBlank(groupId) && Strings.isNotBlank(artifactId)) {
                        CheStack stack = mavenPluginMap.get(groupId + ":" + artifactId);
                        if (stack != null) {
                            return stack;
                        }
                    }
                }
            }
        }
        NodeList dependencies = doc.getElementsByTagName("dependency");
        if (dependencies != null) {
            for (int i = 0, size = dependencies.getLength(); i < size; i++) {
                Node item = dependencies.item(i);
                if (item instanceof Element) {
                    Element element = (Element) item;
                    String groupId = firstElementText(element, "groupId");
                    if (Strings.isNotBlank(groupId)) {
                        CheStack stack = mavenDependencyGroupMap.get(groupId);
                        if (stack != null) {
                            return stack;
                        }
                    }
                }
            }
        }
        return CheStack.JavaCentOS;
    }

    private static String firstElementText(Element element, String name) {
        Element child = DomHelper.firstChild(element, name);
        if (child != null) {
            return child.getTextContent();
        }
        return null;
    }

    /**
     * Returns true if the project has one of the given files
     */
    private static boolean hasFile(UIContext context, org.jboss.forge.addon.projects.Project project, String... fileNames) {
        for (String fileName : fileNames) {
            File file = CommandHelpers.getProjectContextFile(context, project, fileName);
            if (Files.isFile(file)) {
                return true;
            }
        }
        return false;
    }
}
