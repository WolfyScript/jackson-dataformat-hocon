package com.wolfyscript.jackson.dataformat.hocon.deserialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.NullValueProvider;
import com.fasterxml.jackson.databind.deser.std.ObjectArrayDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Objects;
import java.util.TreeMap;

public class ModifiedObjectArrayDeserializer extends ObjectArrayDeserializer {

    public ModifiedObjectArrayDeserializer(JavaType arrayType0, JsonDeserializer<Object> elemDeser, TypeDeserializer elemTypeDeser) {
        super(arrayType0, elemDeser, elemTypeDeser);
    }

    protected ModifiedObjectArrayDeserializer(ObjectArrayDeserializer base, JsonDeserializer<Object> elemDeser, TypeDeserializer elemTypeDeser, NullValueProvider nuller, Boolean unwrapSingle) {
        super(base, elemDeser, elemTypeDeser, nuller, unwrapSingle);
    }

    @Override
    public ModifiedObjectArrayDeserializer withResolved(TypeDeserializer elemTypeDeser, JsonDeserializer<?> elemDeser, NullValueProvider nuller, Boolean unwrapSingle) {
        if ((Objects.equals(unwrapSingle, _unwrapSingle)) && (nuller == _nullProvider)
                && (elemDeser == _elementDeserializer)
                && (elemTypeDeser == _elementTypeDeserializer)) {
            return this;
        }
        return new ModifiedObjectArrayDeserializer(this, (JsonDeserializer<Object>) elemDeser, elemTypeDeser,  nuller, unwrapSingle);
    }

    @Override
    protected Object[] handleNonArray(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.isExpectedStartObjectToken()) {
            return deserializeFromMap(p, ctxt, new Object[0]);
        }
        return super.handleNonArray(p, ctxt);
    }

    protected Object[] deserializeFromMap(JsonParser p, DeserializationContext ctxt, Object[] intoValue) throws IOException {
        TreeMap<Integer, Object> sortedElements = new TreeMap<>();
        String key;
        if (p.isExpectedStartObjectToken()) {
            key = p.nextFieldName();
        } else {
            JsonToken t = p.currentToken();
            if (t == JsonToken.END_OBJECT) {
                return intoValue;
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
                    continue;
                }
            } catch (NumberFormatException exception) {
                // Keys that are not positive integers can be skipped.
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
                Object value;
                if (_elementTypeDeserializer == null) {
                    value = _elementDeserializer.deserialize(p, ctxt);
                } else {
                    value = _elementDeserializer.deserializeWithType(p, ctxt, _elementTypeDeserializer);
                }
                sortedElements.put(index, value);
            } catch (Exception e) {
                wrapAndThrow(ctxt, e, intoValue, key);
            }
        }

        int offset = intoValue.length;
        Object[] result = (Object[]) Array.newInstance(_elementClass, offset + sortedElements.size());
        System.arraycopy(intoValue, 0, result, 0, intoValue.length);
        int i = offset;
        for (Object value : sortedElements.values()) {
            result[i] = value;
            i++;
        }
        return result;
    }
}
