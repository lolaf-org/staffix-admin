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
package org.lolaf.staffix.admin.server.schema;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.file.Path;
import java.util.List;

class SchemaValidator {

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private final Schema schema;

    SchemaValidator(ObjectNode schema) {
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(schema);
    }

    List<String> errors(String yaml) {
        return schema.validate(YAML.readTree(yaml)).stream().map(Error::toString).toList();
    }

    List<String> errors(Path yaml) {
        return schema.validate(YAML.readTree(yaml.toFile())).stream().map(Error::toString).toList();
    }
}
