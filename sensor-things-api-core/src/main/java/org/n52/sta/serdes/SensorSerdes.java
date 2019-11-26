/*
 * Copyright (C) 2018-2019 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.sta.serdes;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.n52.series.db.beans.ProcedureEntity;
import org.n52.series.db.beans.ProcedureHistoryEntity;
import org.n52.series.db.beans.sta.SensorEntity;
import org.n52.sta.serdes.json.JSONSensor;
import org.n52.sta.serdes.model.STAEntityDefinition;
import org.n52.sta.serdes.model.SensorEntityDefinition;
import org.n52.sta.serdes.model.ElementWithQueryOptions.SensorWithQueryOptions;
import org.n52.sta.service.query.QueryOptions;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public class SensorSerdes {

    public static class SensorSerializer extends AbstractSTASerializer<SensorWithQueryOptions> {

        private static final String STA_SENSORML_2 = "http://www.opengis.net/doc/IS/SensorML/2.0";
        private static final String SENSORML_2 = "http://www.opengis.net/sensorml/2.0";

        public SensorSerializer(String rootUrl) {
            super(SensorWithQueryOptions.class);
            this.rootUrl = rootUrl;
            this.entitySetName = SensorEntityDefinition.entitySetName;
        }

        @Override
        public void serialize(SensorWithQueryOptions value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            gen.writeStartObject();
            ProcedureEntity sensor = value.getEntity();
            QueryOptions options = value.getQueryOptions();

            Set<String> fieldsToSerialize = null;
            boolean hasSelectOption = false;
            if (options != null) {
                hasSelectOption = options.hasSelectOption();
                if (hasSelectOption) {
                    fieldsToSerialize = options.getSelectOption();
                }
            }
            // olingo @iot links
            if (!hasSelectOption || fieldsToSerialize.contains(STAEntityDefinition.PROP_ID)) {
                writeId(gen, sensor.getIdentifier());
            }
            if (!hasSelectOption || fieldsToSerialize.contains(STAEntityDefinition.PROP_SELF_LINK)) {
                writeSelfLink(gen, sensor.getIdentifier());
            }

            // actual properties
            if (!hasSelectOption || fieldsToSerialize.contains(STAEntityDefinition.PROP_NAME)) {
                gen.writeStringField(STAEntityDefinition.PROP_NAME, sensor.getName());
            }
            if (!hasSelectOption || fieldsToSerialize.contains(STAEntityDefinition.PROP_DESCRIPTION)) {
                gen.writeStringField(STAEntityDefinition.PROP_DESCRIPTION, sensor.getDescription());
            }
            if (!hasSelectOption || fieldsToSerialize.contains(STAEntityDefinition.PROP_ENCODINGTYPE)) {
                String format = sensor.getFormat().getFormat();
                if (format.equalsIgnoreCase(SENSORML_2)) {
                    format = STA_SENSORML_2;
                }
                gen.writeObjectField(STAEntityDefinition.PROP_ENCODINGTYPE, format);
            }

            if (!hasSelectOption || fieldsToSerialize.contains(STAEntityDefinition.PROP_METADATA)) {
                String metadata = "metadata";
                if (sensor.getDescriptionFile() != null && !sensor.getDescriptionFile().isEmpty()) {
                    metadata = sensor.getDescriptionFile();
                } else if (sensor.hasProcedureHistory()) {
                    Optional<ProcedureHistoryEntity> history =
                            sensor.getProcedureHistory().stream().filter(h -> h.getEndTime() == null).findFirst();
                    if (history.isPresent()) {
                        metadata = history.get().getXml();
                    }
                }
                gen.writeStringField(STAEntityDefinition.PROP_METADATA, metadata);
            }

            // navigation properties
            for (String navigationProperty : SensorEntityDefinition.navigationProperties) {
                if (!hasSelectOption || fieldsToSerialize.contains(navigationProperty)) {
                    writeNavigationProp(gen, navigationProperty, sensor.getIdentifier());
                }
            }
            //TODO: Deal with $expand
            gen.writeEndObject();
        }
    }

    public static class SensorDeserializer extends JsonDeserializer<SensorEntity> {

        @Override
        public SensorEntity deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.readValueAs(JSONSensor.class).toEntity();
        }
    }
}
