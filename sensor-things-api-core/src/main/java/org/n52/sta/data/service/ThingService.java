/*
 * Copyright (C) 2012-2018 52°North Initiative for Geospatial Open Source
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
package org.n52.sta.data.service;

import static org.n52.sta.edm.provider.entities.DatastreamEntityProvider.ET_DATASTREAM_NAME;
import static org.n52.sta.edm.provider.entities.HistoricalLocationEntityProvider.ET_HISTORICAL_LOCATION_NAME;
import static org.n52.sta.edm.provider.entities.LocationEntityProvider.ET_LOCATION_NAME;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.joda.time.DateTime;
import org.n52.series.db.beans.PlatformEntity;
import org.n52.series.db.beans.sta.DatastreamEntity;
import org.n52.series.db.beans.sta.HistoricalLocationEntity;
import org.n52.series.db.beans.sta.LocationEntity;
import org.n52.sta.data.query.ThingQuerySpecifications;
import org.n52.sta.data.repositories.ThingRepository;
import org.n52.sta.data.service.EntityServiceRepository.EntityTypes;
import org.n52.sta.mapping.ThingMapper;
import org.n52.sta.service.query.QueryOptions;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 *
 * @author <a href="mailto:s.drost@52north.org">Sebastian Drost</a>
 */
@Component
public class ThingService extends AbstractSensorThingsEntityService<ThingRepository, PlatformEntity> {

    private ThingMapper mapper;

    private final static ThingQuerySpecifications tQS = new ThingQuerySpecifications();

    public ThingService(ThingRepository repository, ThingMapper mapper) {
        super(repository);
        this.mapper = mapper;
    }
    
    @Override
    public EntityTypes getType() {
        return EntityTypes.Thing;
    }

    @Override
    public EntityCollection getEntityCollection(QueryOptions queryOptions) throws ODataApplicationException {
        EntityCollection retEntitySet = new EntityCollection();
        Specification<PlatformEntity> filter = getFilterPredicate(PlatformEntity.class, queryOptions, null, null);
        getRepository().findAll(filter, createPageableRequest(queryOptions)).forEach(t -> retEntitySet.getEntities().add(mapper.createEntity(t)));
        return retEntitySet;
    }

    @Override
    public Entity getEntity(Long id) {
        Optional<PlatformEntity> entity = getRepository().findById(id);
        return entity.isPresent() ? mapper.createEntity(entity.get()) : null;
    }

    @Override
    public EntityCollection getRelatedEntityCollection(Long sourceId, EdmEntityType sourceEntityType, QueryOptions queryOptions) throws ODataApplicationException {
        Specification<PlatformEntity> filter = tQS.withRelatedLocation(sourceId);

        filter = filter.and(getFilterPredicate(PlatformEntity.class, queryOptions, null, null));       
        Iterable<PlatformEntity> things = getRepository().findAll(filter, createPageableRequest(queryOptions));

        EntityCollection retEntitySet = new EntityCollection();
        things.forEach(t -> retEntitySet.getEntities().add(mapper.createEntity(t)));
        return retEntitySet;
    }

    @Override
    public long getRelatedEntityCollectionCount(Long sourceId, EdmEntityType sourceEntityType) {
        return getRepository().count(tQS.withRelatedLocation(sourceId));
    }

    @Override
    public boolean existsEntity(Long id) {
        return getRepository().existsById(id);
    }

    @Override
    public boolean existsRelatedEntity(Long sourceId, EdmEntityType sourceEntityType) {
        return this.existsRelatedEntity(sourceId, sourceEntityType, null);
    }

    @Override
    public boolean existsRelatedEntity(Long sourceId, EdmEntityType sourceEntityType, Long targetId) {
        switch(sourceEntityType.getFullQualifiedName().getFullQualifiedNameAsString()) {
        case "iot.Location": {
            Specification<PlatformEntity> filter = tQS.withRelatedLocation(sourceId);
            if (targetId != null) {
                filter = filter.and(tQS.withId(targetId));
            }
            return getRepository().count(filter) > 0;
        }
        default: return false;
        }
    }

    @Override
    public OptionalLong getIdForRelatedEntity(Long sourceId, EdmEntityType sourceEntityType) {
        return this.getIdForRelatedEntity(sourceId, sourceEntityType, null);
    }

    @Override
    public OptionalLong getIdForRelatedEntity(Long sourceId, EdmEntityType sourceEntityType, Long targetId) {
        Optional<PlatformEntity> thing = this.getRelatedEntityRaw(sourceId, sourceEntityType, targetId);
        if (thing.isPresent()) {
            return OptionalLong.of(thing.get().getId());
        } else {
            return OptionalLong.empty();
        }
    }

    @Override
    public Entity getRelatedEntity(Long sourceId, EdmEntityType sourceEntityType) {
        return this.getRelatedEntity(sourceId, sourceEntityType, null);
    }

    @Override
    public Entity getRelatedEntity(Long sourceId, EdmEntityType sourceEntityType, Long targetId) {
        Optional<PlatformEntity> thing = this.getRelatedEntityRaw(sourceId, sourceEntityType, targetId);
        if (thing.isPresent()) {
            return mapper.createEntity(thing.get());
        } else {
            return null;
        }
    }

    /**
     * Retrieves Thing Entity with Relation to sourceEntity from Database.
     * Returns empty if Thing is not found or Entities are not related.
     * 
     * @param sourceId Id of the Source Entity
     * @param sourceEntityType Type of the Source Entity
     * @param targetId Id of the Thing to be retrieved
     * @return Optional<PlatformEntity> Requested Entity
     */
    private Optional<PlatformEntity> getRelatedEntityRaw(Long sourceId, EdmEntityType sourceEntityType, Long targetId) {
        Specification<PlatformEntity> filter;
        switch(sourceEntityType.getFullQualifiedName().getFullQualifiedNameAsString()) {
        case "iot.HistoricalLocation": {
            filter = tQS.withRelatedHistoricalLocation(sourceId);
            break;
        }
        case "iot.Datastream": {
            filter = tQS.withRelatedDatastream(sourceId);
            break;
        }
        case "iot.Location": {
            filter = tQS.withRelatedLocation(sourceId);
            break;
        }
        default: return Optional.empty();
        }

        if (targetId != null) {
            filter = filter.and(tQS.withId(targetId));
        }
        return getRepository().findOne(filter);
    }
    
    @Override
    public long getCount(QueryOptions queryOptions) throws ODataApplicationException {
        return getRepository().count(getFilterPredicate(PlatformEntity.class, queryOptions, null, null));
    }

    @Override
    public PlatformEntity create(PlatformEntity thing) throws ODataApplicationException {
        if (!thing.isProcesssed()) {
            if (thing.getId() != null && !thing.isSetName()) {
                return getRepository().findById(thing.getId()).get();
            }
            if (getRepository().existsByName(thing.getName())) {
                Optional<PlatformEntity> optional = getRepository().findByName(thing.getName());
                return optional.isPresent() ? optional.get() : null;
            }
            thing.setProcesssed(true);
            processLocations(thing);
            thing = getRepository().save(thing);
            processHistoricalLocations(thing);
            processDatastreams(thing);
            thing = getRepository().save(thing);
        }
        return thing;
        
    }

    @Override
    public PlatformEntity update(PlatformEntity entity, HttpMethod method) throws ODataApplicationException {
        checkUpdate(entity);
        if (HttpMethod.PATCH.equals(method)) {
            Optional<PlatformEntity> existing = getRepository().findById(entity.getId());
            if (existing.isPresent()) {
                PlatformEntity merged = mapper.merge(existing.get(), entity);
                if (entity.hasLocationEntities()) {
                    merged.setLocations(entity.getLocations());
                    processLocations(merged);
                    merged = getRepository().save(merged);
                    processHistoricalLocations(merged);
                }
                return getRepository().save(merged);
            }
            throw new ODataApplicationException("Entity not found.",
                    HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        } else if (HttpMethod.PUT.equals(method)) {
            throw new ODataApplicationException("Http PUT is not yet supported!",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.getDefault());
        }
        throw new ODataApplicationException("Invalid http method for updating entity!",
                HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
    }
    
    private void checkUpdate(PlatformEntity thing) throws ODataApplicationException {
        if (thing.hasLocationEntities()) {
            for (LocationEntity location : thing.getLocations()) {
                checkInlineLocation(location);
            }
        }
        if (thing.hasDatastreams()) {
            for (DatastreamEntity datastream : thing.getDatastreams()) {
                checkInlineDatastream(datastream);
            }
        }
    }

    @Override
    public PlatformEntity update(PlatformEntity entity) throws ODataApplicationException {
        return getRepository().save(entity);
    }

    @Override
    public void delete(Long id) throws ODataApplicationException {
        if (getRepository().existsById(id)) {
            PlatformEntity thing = getRepository().getOne(id);
            // delete datastreams
            thing.getDatastreams().forEach(d -> {
                try {
                    getDatastreamService().delete(d.getId());
                } catch (ODataApplicationException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });
            // delete historicalLocation
            thing.getHistoricalLocations().forEach(hl -> {
                try {
                    getHistoricalLocationService().delete(hl);
                } catch (ODataApplicationException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });
            getRepository().deleteById(id);
        } else {
        throw new ODataApplicationException("Entity not found.",
                HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }
    }

    @Override
    public void delete(PlatformEntity entity) throws ODataApplicationException {
        getRepository().deleteById(entity.getId());
    }

    private void processDatastreams(PlatformEntity thing) throws ODataApplicationException {
       if (thing.hasDatastreams()) {
           Set<DatastreamEntity> datastreams = new LinkedHashSet<>();
           for (DatastreamEntity datastream : thing.getDatastreams()) {
               datastream.setThing(thing);
               DatastreamEntity optionalDatastream = getDatastreamService().create(datastream);
               datastreams.add(optionalDatastream != null ? optionalDatastream : datastream);
           }
           thing.setDatastreams(datastreams);
       }
    }

    private void processLocations(PlatformEntity thing) throws ODataApplicationException {
        if (thing.hasLocationEntities()) {
            Set<LocationEntity> locations = new LinkedHashSet<>();
            for (LocationEntity location : thing.getLocations()) {
                LocationEntity optionalLocation = getLocationService().create(location);
                locations.add(optionalLocation != null ? optionalLocation : location);
            }
            thing.setLocations(locations);
        }
    }

    private void processHistoricalLocations(PlatformEntity thing) throws ODataApplicationException {
        if (thing != null && thing.hasLocationEntities()) {
            Set<HistoricalLocationEntity> historicalLocations = thing.hasHistoricalLocations()
                    ? new LinkedHashSet<>(thing.getHistoricalLocations())
                    : new LinkedHashSet<>();
            HistoricalLocationEntity historicalLocation = new HistoricalLocationEntity();
            historicalLocation.setThing(thing);
            historicalLocation.setTime(DateTime.now().toDate());
            historicalLocation.setProcesssed(true);
            HistoricalLocationEntity createdHistoricalLocation =
                    getHistoricalLocationService().createOrUpdate(historicalLocation);
            if (createdHistoricalLocation != null) {
                historicalLocations.add(createdHistoricalLocation);
            }
            for (LocationEntity location : thing.getLocations()) {
                location.setHistoricalLocations(historicalLocations);
                getLocationService().createOrUpdate(location);
            }
            thing.setHistoricalLocations(historicalLocations);
        }
    }

    private AbstractSensorThingsEntityService<?, LocationEntity> getLocationService() {
        return (AbstractSensorThingsEntityService<?, LocationEntity>) getEntityService(EntityTypes.Location);
    }

    private AbstractSensorThingsEntityService<?, HistoricalLocationEntity> getHistoricalLocationService() {
        return (AbstractSensorThingsEntityService<?, HistoricalLocationEntity>) getEntityService(
                EntityTypes.HistoricalLocation);
    }

    private AbstractSensorThingsEntityService<?, DatastreamEntity> getDatastreamService() {
        return (AbstractSensorThingsEntityService<?, DatastreamEntity>) getEntityService(
                EntityTypes.Datastream);
    }

     /* (non-Javadoc)
     * @see org.n52.sta.mapping.AbstractMapper#getRelatedCollections(java.lang.Object)
     */
    @Override
    public Map<String, Set<Long>> getRelatedCollections(Object rawObject) {
        Map<String, Set<Long>> collections = new HashMap<> ();
        Set<Long> set = new HashSet<>();
        PlatformEntity entity = (PlatformEntity) rawObject;
        
        try {
            entity.getLocations().forEach((en)-> {
                set.add(en.getId());
            });
            collections.put(ET_LOCATION_NAME, new HashSet(set));
        } catch(NullPointerException e) {}
        set.clear();
        try {
            entity.getHistoricalLocations().forEach((en) -> {
                set.add(en.getId());
            });
            collections.put(ET_HISTORICAL_LOCATION_NAME,  new HashSet(set));
        } catch(NullPointerException e) {}
        set.clear();
        try {
            entity.getDatastreams().forEach((en) -> {
                set.add(en.getId());
            });
            collections.put(ET_DATASTREAM_NAME,  new HashSet(set));
        } catch(NullPointerException e) {}
        return collections;
    }

}
