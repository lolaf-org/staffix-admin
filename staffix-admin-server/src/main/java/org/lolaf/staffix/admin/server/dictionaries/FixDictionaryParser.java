/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lolaf.staffix.admin.server.dictionaries;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the QuickFIX-style XML staffix generates its encoders from: {@code <fields>}, {@code <components>},
 * {@code <messages>}, {@code <header>} and {@code <trailer>}.
 */
@UtilityClass
public class FixDictionaryParser {

    public static FixDictionary parse(String id, byte[] xml) {
        Element root = document(xml).getDocumentElement();
        Map<String, FixDictionary.Field> fieldsByName = fields(root);
        Map<String, Element> components = new HashMap<>();
        children(child(root, "components"), "component").forEach(c -> components.put(c.getAttribute("name"), c));
        Resolver resolver = new Resolver(fieldsByName, components);

        FixDictionary.FixDictionaryBuilder dictionary = FixDictionary.builder().id(id);
        fieldsByName.values().forEach(f -> dictionary.field(f.getTag(), f));
        children(child(root, "messages"), "message").forEach(m -> dictionary.message(m.getAttribute("msgtype"),
                new FixDictionary.Message(m.getAttribute("msgtype"), m.getAttribute("name"), resolver.structure(m))));
        return dictionary
                .header(resolver.structure(child(root, "header")))
                .trailer(resolver.structure(child(root, "trailer")))
                .build();
    }

    private static Map<String, FixDictionary.Field> fields(Element root) {
        Map<String, FixDictionary.Field> fields = new HashMap<>();
        for (Element field : children(child(root, "fields"), "field")) {
            FixDictionary.Field.FieldBuilder builder = FixDictionary.Field.builder()
                    .tag(Integer.parseInt(field.getAttribute("number")))
                    .name(field.getAttribute("name"))
                    .type(field.getAttribute("type"));
            children(field, "value").forEach(v -> builder.value(v.getAttribute("enum"), v.getAttribute("description")));
            fields.put(field.getAttribute("name"), builder.build());
        }
        return fields;
    }

    @RequiredArgsConstructor
    private static class Resolver {

        private final Map<String, FixDictionary.Field> fields;
        private final Map<String, Element> components;

        FixDictionary.Structure structure(Element container) {
            FixDictionary.Structure.StructureBuilder structure = FixDictionary.Structure.builder();
            if (container != null) {
                collect(container, structure, new ArrayList<>());
            }
            return structure.build();
        }

        /**
         * Expands components in place; {@code order} receives the tags in declaration order, so a group knows its
         * delimiter.
         */
        private void collect(Element container, FixDictionary.Structure.StructureBuilder structure, List<Integer> order) {
            for (Element child : elements(container)) {
                switch (child.getTagName()) {
                    case "field" -> {
                        FixDictionary.Field field = fields.get(child.getAttribute("name"));
                        if (field != null) {
                            structure.tag(field.getTag());
                            order.add(field.getTag());
                        }
                    }
                    case "component" -> {
                        Element component = components.get(child.getAttribute("name"));
                        if (component != null) {
                            collect(component, structure, order);
                        }
                    }
                    case "group" -> {
                        FixDictionary.Field counter = fields.get(child.getAttribute("name"));
                        if (counter == null) {
                            continue;
                        }
                        FixDictionary.Structure.StructureBuilder members = FixDictionary.Structure.builder();
                        List<Integer> memberOrder = new ArrayList<>();
                        collect(child, members, memberOrder);
                        if (!memberOrder.isEmpty()) {
                            structure.tag(counter.getTag());
                            order.add(counter.getTag());
                            structure.group(counter.getTag(),
                                    new FixDictionary.Group(counter.getTag(), memberOrder.getFirst(), members.build()));
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private static Document document(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("Not a FIX dictionary: " + e.getMessage(), e);
        }
    }

    private static Element child(Element parent, String name) {
        return children(parent, name).stream().findFirst().orElse(null);
    }

    private static List<Element> children(Element parent, String name) {
        return parent == null ? List.of() : elements(parent).stream().filter(e -> e.getTagName().equals(name)).toList();
    }

    private static List<Element> elements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) nodes.item(i));
            }
        }
        return elements;
    }
}
