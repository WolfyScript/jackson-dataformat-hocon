package com.wolfyscript.jackson.dataformat.hocon.deserialization;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.NullValueProvider;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.ContainerDeserializerBase;
import com.fasterxml.jackson.databind.deser.std.MapDeserializer;
import com.fasterxml.jackson.databind.introspect.AnnotatedWithParams;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.LogicalType;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class ModifiedStringCollectionDeserializer extends ContainerDeserializerBase<Collection<String>> implements ContextualDeserializer {
    private static final long serialVersionUID = 1L;

    // // Configuration

    /**
     * Value deserializer to use, if NOT the standard one
     * (if it is, will be null).
     */
    protected final JsonDeserializer<String> _valueDeserializer;

    // // Instance construction settings:

    /**
     * Instantiator used in case custom handling is needed for creation.
     */
    protected final ValueInstantiator _valueInstantiator;

    /**
     * Deserializer that is used iff delegate-based creator is
     * to be used for deserializing from JSON Object.
     */
    protected final JsonDeserializer<Object> _delegateDeserializer;

    // NOTE: no PropertyBasedCreator, as JSON Arrays have no properties

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public ModifiedStringCollectionDeserializer(JavaType collectionType, JsonDeserializer<?> valueDeser, ValueInstantiator valueInstantiator) {
        this(collectionType, valueInstantiator, null, valueDeser, valueDeser, null);
    }

    @SuppressWarnings("unchecked")
    protected ModifiedStringCollectionDeserializer(JavaType collectionType, ValueInstantiator valueInstantiator, JsonDeserializer<?> delegateDeser, JsonDeserializer<?> valueDeser, NullValueProvider nuller, Boolean unwrapSingle) {
        super(collectionType, nuller, unwrapSingle);
        _valueDeserializer = (JsonDeserializer<String>) valueDeser;
        _valueInstantiator = valueInstantiator;
        _delegateDeserializer = (JsonDeserializer<Object>) delegateDeser;
    }

    protected ModifiedStringCollectionDeserializer withResolved(JsonDeserializer<?> delegateDeser, JsonDeserializer<?> valueDeser, NullValueProvider nuller, Boolean unwrapSingle) {
        if ((Objects.equals(_unwrapSingle, unwrapSingle)) && (_nullProvider == nuller)
                && (_valueDeserializer == valueDeser) && (_delegateDeserializer == delegateDeser)) {
            return this;
        }
        return new ModifiedStringCollectionDeserializer(_containerType, _valueInstantiator,
                delegateDeser, valueDeser, nuller, unwrapSingle);
    }

    @Override // since 2.5
    public boolean isCachable() {
        // 26-Mar-2015, tatu: Important: prevent caching if custom deserializers via annotations
        //    are involved
        return (_valueDeserializer == null) && (_delegateDeserializer == null);
    }

    @Override // since 2.12
    public LogicalType logicalType() {
        return LogicalType.Collection;
    }

    /*
    /**********************************************************
    /* Validation, post-processing
    /**********************************************************
     */
    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) throws JsonMappingException {
        // May need to resolve types for delegate-based creators:
        JsonDeserializer<Object> delegate = null;
        if (_valueInstantiator != null) {
            // [databind#2324]: check both array-delegating and delegating
            AnnotatedWithParams delegateCreator = _valueInstantiator.getArrayDelegateCreator();
            if (delegateCreator != null) {
                JavaType delegateType = _valueInstantiator.getArrayDelegateType(ctxt.getConfig());
                delegate = findDeserializer(ctxt, delegateType, property);
            } else if ((delegateCreator = _valueInstantiator.getDelegateCreator()) != null) {
                JavaType delegateType = _valueInstantiator.getDelegateType(ctxt.getConfig());
                delegate = findDeserializer(ctxt, delegateType, property);
            }
        }
        JsonDeserializer<?> valueDeser = _valueDeserializer;
        final JavaType valueType = _containerType.getContentType();
        if (valueDeser == null) {
            // [databind#125]: May have a content converter
            valueDeser = findConvertingContentDeserializer(ctxt, property, valueDeser);
            if (valueDeser == null) {
                // And we may also need to get deserializer for String
                valueDeser = ctxt.findContextualValueDeserializer(valueType, property);
            }
        } else { // if directly assigned, probably not yet contextual, so:
            valueDeser = ctxt.handleSecondaryContextualization(valueDeser, property, valueType);
        }
        // 11-Dec-2015, tatu: Should we pass basic `Collection.class`, or more refined? Mostly
        //   comes down to "List vs Collection" I suppose... for now, pass Collection
        Boolean unwrapSingle = findFormatFeature(ctxt, property, Collection.class,
                JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        NullValueProvider nuller = findContentNullProvider(ctxt, property, valueDeser);
        if (isDefaultDeserializer(valueDeser)) {
            valueDeser = null;
        }
        return withResolved(delegate, valueDeser, nuller, unwrapSingle);
    }

    /*
    /**********************************************************
    /* ContainerDeserializerBase API
    /**********************************************************
     */

    @SuppressWarnings("unchecked")
    @Override
    public JsonDeserializer<Object> getContentDeserializer() {
        JsonDeserializer<?> deser = _valueDeserializer;
        return (JsonDeserializer<Object>) deser;
    }

    @Override
    public ValueInstantiator getValueInstantiator() {
        return _valueInstantiator;
    }

    /*
    /**********************************************************
    /* JsonDeserializer API
    /**********************************************************
     */

    @SuppressWarnings("unchecked")
    @Override
    public Collection<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (_delegateDeserializer != null) {
            return (Collection<String>) _valueInstantiator.createUsingDelegate(ctxt,
                    _delegateDeserializer.deserialize(p, ctxt));
        }
        final Collection<String> result = (Collection<String>) _valueInstantiator.createUsingDefault(ctxt);
        return deserialize(p, ctxt, result);
    }

    @Override
    public Collection<String> deserialize(JsonParser p, DeserializationContext ctxt, Collection<String> result) throws IOException {
        // Ok: must point to START_ARRAY
        if (!p.isExpectedStartArrayToken()) {
            return handleNonArray(p, ctxt, result);
        }

        if (_valueDeserializer != null) {
            return deserializeUsingCustom(p, ctxt, result, _valueDeserializer);
        }
        try {
            while (true) {
                // First the common case:
                String value = p.nextTextValue();
                if (value != null) {
                    result.add(value);
                    continue;
                }
                JsonToken t = p.currentToken();
                if (t == JsonToken.END_ARRAY) {
                    break;
                }
                if (t == JsonToken.VALUE_NULL) {
                    if (_skipNullValues) {
                        continue;
                    }
                    value = (String) _nullProvider.getNullValue(ctxt);
                } else {
                    value = _parseString(p, ctxt);
                }
                result.add(value);
            }
        } catch (Exception e) {
            throw JsonMappingException.wrapWithPath(e, result, result.size());
        }
        return result;
    }

    private Collection<String> deserializeUsingCustom(JsonParser p, DeserializationContext ctxt, Collection<String> result, final JsonDeserializer<String> deser) throws IOException {
        try {
            while (true) {
                /* 30-Dec-2014, tatu: This may look odd, but let's actually call method
                 *   that suggest we are expecting a String; this helps with some formats,
                 *   notably XML. Note, however, that while we can get String, we can't
                 *   assume that's what we use due to custom deserializer
                 */
                String value;
                if (p.nextTextValue() == null) {
                    JsonToken t = p.currentToken();
                    if (t == JsonToken.END_ARRAY) {
                        break;
                    }
                    // Ok: no need to convert Strings, but must recognize nulls
                    if (t == JsonToken.VALUE_NULL) {
                        if (_skipNullValues) {
                            continue;
                        }
                        value = (String) _nullProvider.getNullValue(ctxt);
                    } else {
                        value = deser.deserialize(p, ctxt);
                    }
                } else {
                    value = deser.deserialize(p, ctxt);
                }
                result.add(value);
            }
        } catch (Exception e) {
            throw JsonMappingException.wrapWithPath(e, result, result.size());
        }
        return result;
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {
        // In future could check current token... for now this should be enough:
        return typeDeserializer.deserializeTypedFromArray(p, ctxt);
    }

    /**
     * Helper method called when current token is not START_ARRAY. Will either
     * throw an exception, or try to handle value as if member of implicit
     * array, depending on configuration.
     */
    @SuppressWarnings("unchecked")
    private final Collection<String> handleNonArray(JsonParser p, DeserializationContext ctxt, Collection<String> result) throws IOException {
        if (p.isExpectedStartObjectToken()) {
            return deserializeFromMap(p, ctxt, result);
        }
        // implicit arrays from single values?
        boolean canWrap = (_unwrapSingle == Boolean.TRUE) ||
                ((_unwrapSingle == null) &&
                        ctxt.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY));
        if (!canWrap) {
            if (p.hasToken(JsonToken.VALUE_STRING)) {
                return _deserializeFromString(p, ctxt);
            }
            return (Collection<String>) ctxt.handleUnexpectedToken(_containerType, p);
        }
        // Strings are one of "native" (intrinsic) types, so there's never type deserializer involved
        JsonDeserializer<String> valueDes = _valueDeserializer;
        JsonToken t = p.currentToken();

        String value;

        if (t == JsonToken.VALUE_NULL) {
            // 03-Feb-2017, tatu: Does this work?
            if (_skipNullValues) {
                return result;
            }
            value = (String) _nullProvider.getNullValue(ctxt);
        } else {
            try {
                value = (valueDes == null) ? _parseString(p, ctxt) : valueDes.deserialize(p, ctxt);
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, result, result.size());
            }
        }
        result.add(value);
        return result;
    }

    protected Collection<String> deserializeFromMap(JsonParser p, DeserializationContext ctxt, Collection<String> result) throws IOException {
        final JsonDeserializer<String> valueDes = _valueDeserializer;
        TreeMap<Integer, String> sortedElements = new TreeMap<>();
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
                String value;
                if (t == JsonToken.VALUE_NULL) {
                    // 03-Feb-2017, tatu: Does this work?
                    if (_skipNullValues) {
                        return result;
                    }
                    value = (String) _nullProvider.getNullValue(ctxt);
                } else {
                    try {
                        value = (valueDes == null) ? _parseString(p, ctxt) : valueDes.deserialize(p, ctxt);
                    } catch (Exception e) {
                        throw JsonMappingException.wrapWithPath(e, result, result.size());
                    }
                }
                sortedElements.put(index, value);
            } catch (Exception e) {
                wrapAndThrow(ctxt, e, result, key);
            }
        }
        result.addAll(sortedElements.values());
        return result;
    }
}
