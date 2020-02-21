/*
 * Copyright (C) 2018-2020 52°North Initiative for Geospatial Open Source
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
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.n52.sta.mqtt.core.subscription;

import org.n52.series.db.beans.HibernateRelations;
import org.n52.series.db.beans.PhenomenonEntity;
import org.n52.sta.utils.STARequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * @author <a href="mailto:s.drost@52north.org">Sebastian Drost</a>
 */
public class MqttEntitySubscription extends AbstractMqttSubscription {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttEntitySubscription.class);

    private String wantedIdentifier;

    public MqttEntitySubscription(String topic, Matcher mt) {
        super(topic, mt);

        // Referenced Entity
        // E.g. /Datastream(52)/Sensor
        if (mt.group(STARequestUtils.GROUPNAME_WANTED_IDENTIFIER) == null) {
            sourceEntityType = mt.group(STARequestUtils.GROUPNAME_SOURCE_NAME);
            sourceId = mt.group(STARequestUtils.GROUPNAME_SOURCE_IDENTIFIER);
            wantedEntityType = mt.group(STARequestUtils.GROUPNAME_WANTED_NAME);
            Assert.notNull(sourceId, "Unable to parse topic. Could not extract sourceId");
            Assert.notNull(sourceEntityType, "Unable to parse topic. Could not extract sourceEntityType");
        } else {
            // Direct Entity
            // E.g. /Things(52)
            wantedEntityType = mt.group(STARequestUtils.GROUPNAME_WANTED_NAME);
            wantedIdentifier = mt.group(STARequestUtils.GROUPNAME_WANTED_IDENTIFIER);
            Assert.notNull(wantedIdentifier, "Unable to parse topic. Could not extract wantedIdentifier");
        }

        Assert.notNull(wantedEntityType, "Unable to parse topic. Could not extract wantedEntityType");
        LOGGER.debug(this.toString());
    }

    @Override
    public boolean matches(HibernateRelations.HasIdentifier entity,
                           String realEntityType,
                           Map<String, Set<String>> collections,
                           Set<String> differenceMap) {

        // Check type and fail-fast on type mismatch
        if (!(wantedEntityType.equals(realEntityType))) {
            return false;
        }

        // Direct Entity
        if (wantedIdentifier != null) {
            // Use special case for PhenomenonEntity as staIdentifier is used for addressing
            if (entity instanceof PhenomenonEntity) {
                return wantedIdentifier.equals(((PhenomenonEntity) entity).getStaIdentifier());
            } else {
                return wantedIdentifier.equals(entity.getIdentifier());
            }
        } else {
            // Referenced Entity
            // Check if Entity belongs to collection of this Subscription
            //TODO(specki): check if this acutally works as names have changed
            if (collections != null) {
                for (Map.Entry<String, Set<String>> collection : collections.entrySet()) {
                    if (collection.getKey().equals(sourceEntityType)) {
                        for (String id : collection.getValue()) {
                            if (id.equals(sourceId)) {
                                return true;
                            }
                        }
                        return false;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        String base = super.toString();
        return new StringBuilder()
                .append(base)
                .deleteCharAt(base.length() - 1)
                .append(",wantedIdentifier=")
                .append(wantedIdentifier)
                .append("]")
                .toString();
    }
}