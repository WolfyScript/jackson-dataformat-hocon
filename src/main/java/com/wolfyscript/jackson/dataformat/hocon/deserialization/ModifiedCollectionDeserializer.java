package com.wolfyscript.jackson.dataformat.hocon.deserialization;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.NullValueProvider;
import com.fasterxml.jackson.databind.deser.UnresolvedForwardReference;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.CollectionDeserializer;
import com.fasterxml.jackson.databind.deser.std.MapDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class ModifiedCollectionDeserializer extends CollectionDeserializer {

    protected ObjectMapper mapper;

    public ModifiedCollectionDeserializer(ObjectMapper mapper, JavaType collectionType, JsonDeserializer<Object> valueDeser, TypeDeserializer valueTypeDeser, ValueInstantiator valueInstantiator) {
        super(collectionType, valueDeser, valueTypeDeser, valueInstantiator);
        this.mapper = mapper;
    }

    protected ModifiedCollectionDeserializer(ObjectMapper mapper, JavaType collectionType, JsonDeserializer<Object> valueDeser, TypeDeserializer valueTypeDeser, ValueInstantiator valueInstantiator, JsonDeserializer<Object> delegateDeser, NullValueProvider nuller, Boolean unwrapSingle) {
        super(collectionType, valueDeser, valueTypeDeser, valueInstantiator, delegateDeser, nuller, unwrapSingle);
        this.mapper = mapper;
    }

    protected ModifiedCollectionDeserializer(ObjectMapper mapper, CollectionDeserializer src) {
        super(src);
        this.mapper = mapper;
    }

    @Override
    protected CollectionDeserializer withResolved(JsonDeserializer<?> dd, JsonDeserializer<?> vd, TypeDeserializer vtd, NullValueProvider nuller, Boolean unwrapSingle) {
        return new ModifiedCollectionDeserializer(mapper, super.withResolved(dd, vd, vtd, nuller, unwrapSingle));
    }

    @Override
    public Collection<Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.isExpectedStartObjectToken()) {
            return deserializeFromMap(p, ctxt, createDefaultInstance(ctxt));
        }
        return super.deserialize(p, ctxt);
    }

    @Override
    public Collection<Object> deserialize(JsonParser p, DeserializationContext ctxt, Collection<Object> result) throws IOException {
        if (p.isExpectedStartObjectToken()) {
            return deserializeFromMap(p, ctxt, result);
        }
        return super.deserialize(p, ctxt, result);
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {
        if (p.isExpectedStartObjectToken()) {
            return typeDeserializer.deserializeTypedFromObject(p, ctxt);
        }
        return super.deserializeWithType(p, ctxt, typeDeserializer);
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer, Collection<Object> intoValue) throws IOException {
        return super.deserializeWithType(p, ctxt, typeDeserializer, intoValue);
    }


    protected Collection<Object> deserializeFromMap(JsonParser p, DeserializationContext ctxt, Collection<Object> result) throws IOException {
        final JsonDeserializer<Object> valueDes = _valueDeserializer;
        final TypeDeserializer typeDeser = _valueTypeDeserializer;

        TreeMap<Integer, Object> sortedElements = new TreeMap<>();

        // Note: assumption is that Object Id handling can't really work with merging
        // and thereby we can (and should) just drop that part

        String key;
        if (p.isExpectedStartObjectToken()) {
            key = p.nextFieldName();
        } else {
            JsonToken t = p.currentToken();
            if (t == JsonToken.END_OBJECT) {
                return result;
            }
            if (t != JsonToken.FIELD_NAME) {
                ctxt.reportWrongTokenException(this, JsonToken.FIELD_NAME, null);
            }
            key = p.currentName();
        }

        for (; key != null; key = p.nextFieldName()) {
            JsonToken t = p.nextToken();
            int index;
            try {
                index = Integer.parseInt(key);
                if (index < 0) {
                    p.skipChildren();
                    continue;
                }
            } catch (NumberFormatException exception) {
                // Keys that are not positive integers can be skipped.
                p.skipChildren();
                continue;
            }
            try {
                // Note: must handle null explicitly here, can't merge etc
                if (t == JsonToken.VALUE_NULL) {
                    if (_skipNullValues) {
                        continue;
                    }
                    sortedElements.put(index, _nullProvider.getNullValue(ctxt));
                    continue;
                }
                Object old = sortedElements.get(index); // This does not really work, as the index in the
                Object value;
                if (old != null) {
                    if (typeDeser == null) {
                        value = valueDes.deserialize(p, ctxt, old);
                    } else {
                        value = valueDes.deserializeWithType(p, ctxt, typeDeser, old);
                    }
                } else if (typeDeser == null) {
                    value = valueDes.deserialize(p, ctxt);
                } else {
                    value = valueDes.deserializeWithType(p, ctxt, typeDeser);
                }
                if (value != old) {
                    sortedElements.put(index, value);
                }
            } catch (Exception e) {
                wrapAndThrow(ctxt, e, result, key);
            }
        }

        Iterator<Object> iterator = result.iterator();
        int i = 0;
        while (iterator.hasNext() && i < sortedElements.size()) {
            iterator.next();
            iterator.remove();
            i++;
        }
        Collection<Object> objects = createDefaultInstance(ctxt);
        objects.addAll(sortedElements.values());
        objects.addAll(result);
        return objects;
    }

}
