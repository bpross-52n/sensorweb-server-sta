/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.n52.sta.mqtt.handler;

import static org.n52.sta.edm.provider.entities.AbstractSensorThingsEntityProvider.PROP_ID;

import java.util.List;
import java.util.Set;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.UriResourceProperty;
import org.n52.sta.mqtt.core.MqttPropertySubscription;
import org.n52.sta.mqtt.core.MqttUtil;
import org.n52.sta.mqtt.request.SensorThingsMqttRequest;
import org.n52.sta.service.handler.AbstractPropertyRequestHandler;
import org.n52.sta.service.query.QueryOptions;
import org.n52.sta.utils.EntityQueryParams;
import org.n52.sta.utils.UriResourceNavigationResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author <a href="mailto:s.drost@52north.org">Sebastian Drost</a>
 */
@Component
public class MqttPropertySubscriptionHandler extends AbstractPropertyRequestHandler<SensorThingsMqttRequest, MqttPropertySubscription> {

    @Autowired
    private UriResourceNavigationResolver navigationResolver;

    @Override
    public MqttPropertySubscription handlePropertyRequest(SensorThingsMqttRequest request) throws ODataApplicationException {
        MqttPropertySubscription subscription = null;

        List<UriResource> resourcePaths = request.getResourcePaths();

        // handle request depending on the number of UriResource paths
        // e.g. the case: sta/Things(id)/Locations(id)/name
        if (resourcePaths.get(1) instanceof UriResourceNavigation) {
            subscription = resolvePropertyForNavigation(request.getTopic(), request.getResourcePaths(), request.getQueryOptions());

            // e.g the case: sta/Things(id)/description
        } else {
            subscription = resolvePropertyForEntity(request.getTopic(), request.getResourcePaths(), request.getQueryOptions());

        }
        return subscription;
    }

    private MqttPropertySubscription resolvePropertyForNavigation(String topic, List<UriResource> resourcePaths, QueryOptions queryOptions) throws ODataApplicationException {
        int i = 0;
        UriResource lastEntitySegment = resourcePaths.get(i);
        // note that the last value for i at the end of the loop is the index
        // fot the UriResourceProperty element
        while (resourcePaths.get(++i) instanceof UriResourceNavigation) {
            lastEntitySegment = resourcePaths.get(i);
        }
        // determine the target query parameters and fetch Entity for it
        EntityQueryParams queryParams = navigationResolver.resolveUriResourceNavigationPaths(resourcePaths.subList(0, i));
        Entity targetEntity = navigationResolver.resolveComplexEntityRequest(lastEntitySegment, queryParams);

        List<UriResource> propertyResourcePaths = resourcePaths.subList(i, resourcePaths.size());
        EdmProperty edmProperty = ((UriResourceProperty) propertyResourcePaths.get(0)).getProperty();

        Set<String> watchedProperties = MqttUtil.translateSTAtoToDbProperty(
                "iot."
                + queryParams.getTargetEntitySet().getEntityType().getName()
                + "."
                + edmProperty.getName());

        MqttPropertySubscription subscription = new MqttPropertySubscription(
                queryParams.getTargetEntitySet(),
                queryParams.getTargetEntitySet().getEntityType(),
                (Long) targetEntity.getProperty(PROP_ID).getValue(),
                edmProperty,
                topic,
                queryOptions,
                watchedProperties);
        return subscription;
    }

    private MqttPropertySubscription resolvePropertyForEntity(String topic, List<UriResource> resourcePaths, QueryOptions queryOptions) throws ODataApplicationException {
        UriResourceEntitySet uriResourceEntitySet = navigationResolver.resolveRootUriResource(resourcePaths.get(0));
        Entity targetEntity = navigationResolver.resolveSimpleEntityRequest(uriResourceEntitySet);
        EdmProperty edmProperty = ((UriResourceProperty) resourcePaths.get(0)).getProperty();

        Set<String> watchedProperties = MqttUtil.translateSTAtoToDbProperty(
                "iot."
                + uriResourceEntitySet.getEntityType().getName()
                + "."
                + edmProperty.getName());

        MqttPropertySubscription subscription = new MqttPropertySubscription(
                uriResourceEntitySet.getEntitySet(),
                uriResourceEntitySet.getEntityType(),
                (Long) targetEntity.getProperty(PROP_ID).getValue(),
                edmProperty,
                topic,
                queryOptions,
                watchedProperties);
        return subscription;
    }

}
