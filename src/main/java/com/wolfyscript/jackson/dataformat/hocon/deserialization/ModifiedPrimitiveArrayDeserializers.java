package com.wolfyscript.jackson.dataformat.hocon.deserialization;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JacksonStdImpl;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.NullValueProvider;
import com.fasterxml.jackson.databind.deser.impl.NullsConstantProvider;
import com.fasterxml.jackson.databind.deser.impl.NullsFailProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.util.AccessPattern;
import com.fasterxml.jackson.databind.util.ArrayBuilders;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Container for deserializers used for instantiating "primitive arrays",
 * arrays that contain non-object java primitive types.
 */
@SuppressWarnings("serial")
public abstract class ModifiedPrimitiveArrayDeserializers<T> extends StdDeserializer<T> implements ContextualDeserializer // since 2.7
{
    /**
     * Specific override for this instance (from proper, or global per-type overrides)
     * to indicate whether single value may be taken to mean an unwrapped one-element array
     * or not. If null, left to global defaults.
     *
     * @since 2.7
     */
    protected final Boolean _unwrapSingle;

    // since 2.9
    private transient Object _emptyValue;

    /**
     * Flag that indicates need for special handling; either failing
     * (throw exception) or skipping
     */
    protected final NullValueProvider _nuller;

    /*
    /********************************************************
    /* Life-cycle
    /********************************************************
     */

    protected ModifiedPrimitiveArrayDeserializers(Class<T> cls) {
        super(cls);
        _unwrapSingle = null;
        _nuller = null;
    }

    /**
     * @since 2.7
     */
    protected ModifiedPrimitiveArrayDeserializers(ModifiedPrimitiveArrayDeserializers<?> base, NullValueProvider nuller, Boolean unwrapSingle) {
        super(base._valueClass);
        _unwrapSingle = unwrapSingle;
        _nuller = nuller;
    }

    public static JsonDeserializer<?> forType(Class<?> rawType) {
        // Start with more common types...
        if (rawType == Integer.TYPE) {
            return IntDeser.instance;
        }
        if (rawType == Long.TYPE) {
            return LongDeser.instance;
        }

        if (rawType == Byte.TYPE) {
            return new ByteDeser();
        }
        if (rawType == Short.TYPE) {
            return new ShortDeser();
        }
        if (rawType == Float.TYPE) {
            return new FloatDeser();
        }
        if (rawType == Double.TYPE) {
            return new DoubleDeser();
        }
        if (rawType == Boolean.TYPE) {
            return new BooleanDeser();
        }
        if (rawType == Character.TYPE) {
            return new CharDeser();
        }
        throw new IllegalStateException();
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) throws JsonMappingException {
        Boolean unwrapSingle = findFormatFeature(ctxt, property, _valueClass,
                JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        NullValueProvider nuller = null;

        Nulls nullStyle = findContentNullStyle(ctxt, property);
        if (nullStyle == Nulls.SKIP) {
            nuller = NullsConstantProvider.skipper();
        } else if (nullStyle == Nulls.FAIL) {
            if (property == null) {
                // 09-Dec-2019, tatu: [databind#2567] need to ensure correct target type
                nuller = NullsFailProvider.constructForRootValue(ctxt.constructType(_valueClass.getComponentType()));
            } else {
                // 09-Dec-2019, tatu: [databind#2567] need to ensure correct target type
                nuller = NullsFailProvider.constructForProperty(property, property.getType().getContentType());
            }
        }
        if ((Objects.equals(unwrapSingle, _unwrapSingle)) && (nuller == _nuller)) {
            return this;
        }
        return withResolved(nuller, unwrapSingle);
    }

    /*
    /********************************************************
    /* Abstract methods for sub-classes to implement
    /********************************************************
     */

    /**
     * @since 2.9
     */
    protected abstract T _concat(T oldValue, T newValue);

    protected abstract T handleSingleElementUnwrapped(JsonParser p, DeserializationContext ctxt) throws IOException;

    protected abstract T handleMapDeserialization(JsonParser p, DeserializationContext ctxt, String startKey) throws IOException;

    /**
     * @since 2.9
     */
    protected abstract ModifiedPrimitiveArrayDeserializers<?> withResolved(NullValueProvider nuller, Boolean unwrapSingle);

    // since 2.9
    protected abstract T _constructEmpty();
    
    /*
    /********************************************************
    /* Default implementations
    /********************************************************
     */

    @Override // since 2.12
    public LogicalType logicalType() {
        return LogicalType.Array;
    }

    @Override // since 2.9
    public Boolean supportsUpdate(DeserializationConfig config) {
        return Boolean.TRUE;
    }

    @Override
    public AccessPattern getEmptyAccessPattern() {
        // Empty values shareable freely
        return AccessPattern.CONSTANT;
    }

    @Override // since 2.9
    public Object getEmptyValue(DeserializationContext ctxt) throws JsonMappingException {
        Object empty = _emptyValue;
        if (empty == null) {
            _emptyValue = empty = _constructEmpty();
        }
        return empty;
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {
        // Should there be separate handling for base64 stuff?
        // for now this should be enough:
        return typeDeserializer.deserializeTypedFromArray(p, ctxt);
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt, T existing) throws IOException {
        T newValue = deserialize(p, ctxt);
        if (existing == null) {
            return newValue;
        }
        int len = Array.getLength(existing);
        if (len == 0) {
            return newValue;
        }
        return _concat(existing, newValue);
    }

    /*
    /********************************************************
    /* Helper methods for sub-classes
    /********************************************************
     */

    @SuppressWarnings("unchecked")
    protected T handleNonArray(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.isExpectedStartObjectToken()) {
            String key;
            if (p.isExpectedStartObjectToken()) {
                key = p.nextFieldName();
            } else {
                JsonToken t = p.currentToken();
                if (t == JsonToken.END_OBJECT) {
                    return _constructEmpty();
                }
                if (t != JsonToken.FIELD_NAME) {
                    ctxt.reportWrongTokenException(this, JsonToken.FIELD_NAME, null);
                }
                key = p.currentName();
            }
            return handleMapDeserialization(p, ctxt, key);
        }
        // Empty String can become null...
        if (p.hasToken(JsonToken.VALUE_STRING)) {
            return _deserializeFromString(p, ctxt);
        }
        boolean canWrap = (_unwrapSingle == Boolean.TRUE) ||
                ((_unwrapSingle == null) &&
                        ctxt.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY));
        if (canWrap) {
            return handleSingleElementUnwrapped(p, ctxt);
        }
        return (T) ctxt.handleUnexpectedToken(_valueClass, p);
    }

    protected int _nextEntry(JsonParser p) throws IOException {
        return _indexForEntry(p, p.nextFieldName());
    }

    protected int _indexForEntry(JsonParser p, String key) throws IOException {
        if (key == null) return -2;
        JsonToken t = p.nextToken();
        int index;
        try {
            index = Integer.parseInt(key);
            if (index < 0) {
                p.skipChildren();
                return -1;
            }
        } catch (NumberFormatException exception) {
            // Keys that are not positive integers can be skipped.
            p.skipChildren();
            return -1;
        }
        return index;
    }

    protected void _failOnNull(DeserializationContext ctxt) throws IOException {
        throw InvalidNullException.from(ctxt, null, ctxt.constructType(_valueClass));
    }

    /*
    /********************************************************
    /* Actual deserializers: efficient String[], char[] deserializers
    /********************************************************
     */

    @JacksonStdImpl
    final static class CharDeser extends ModifiedPrimitiveArrayDeserializers<char[]> {
        private static final long serialVersionUID = 1L;

        public CharDeser() {
            super(char[].class);
        }

        protected CharDeser(ModifiedPrimitiveArrayDeserializers.CharDeser base, NullValueProvider nuller, Boolean unwrapSingle) {
            super(base, nuller, unwrapSingle);
        }

        @Override
        protected ModifiedPrimitiveArrayDeserializers<?> withResolved(NullValueProvider nuller,
                                                                      Boolean unwrapSingle) {
            // 11-Dec-2015, tatu: Not sure how re-wrapping would work; omit
            return this;
        }

        @Override
        protected char[] _constructEmpty() {
            return new char[0];
        }

        @Override
        public char[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            /* Won't take arrays, must get a String (could also
             * convert other tokens to Strings... but let's not bother
             * yet, doesn't seem to make sense)
             */
            if (p.hasToken(JsonToken.VALUE_STRING)) {
                // note: can NOT return shared internal buffer, must copy:
                char[] buffer = p.getTextCharacters();
                int offset = p.getTextOffset();
                int len = p.getTextLength();

                char[] result = new char[len];
                System.arraycopy(buffer, offset, result, 0, len);
                return result;
            }
            if (p.isExpectedStartArrayToken()) {
                // Let's actually build as a String, then get chars
                StringBuilder sb = new StringBuilder(64);
                JsonToken t;
                while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                    String str;
                    if (t == JsonToken.VALUE_STRING) {
                        str = p.getText();
                    } else if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                        _verifyNullForPrimitive(ctxt);
                        str = "\0";
                    } else {
                        CharSequence cs = (CharSequence) ctxt.handleUnexpectedToken(Character.TYPE, p);
                        str = cs.toString();
                    }
                    if (str.length() != 1) {
                        ctxt.reportInputMismatch(this,
                                "Cannot convert a JSON String of length %d into a char element of char array", str.length());
                    }
                    sb.append(str.charAt(0));
                }
                return sb.toString().toCharArray();
            }
            // or, maybe an embedded object?
            if (p.hasToken(JsonToken.VALUE_EMBEDDED_OBJECT)) {
                Object ob = p.getEmbeddedObject();
                if (ob == null) return null;
                if (ob instanceof char[]) {
                    return (char[]) ob;
                }
                if (ob instanceof String) {
                    return ((String) ob).toCharArray();
                }
                // 04-Feb-2011, tatu: byte[] can be converted; assuming base64 is wanted
                if (ob instanceof byte[]) {
                    return Base64Variants.getDefaultVariant().encode((byte[]) ob, false).toCharArray();
                }
                // not recognized, just fall through
            }
            if (p.isExpectedStartObjectToken()) {
                return handleNonArray(p, ctxt);
            }
            return (char[]) ctxt.handleUnexpectedToken(_valueClass, p);
        }

        @Override
        protected char[] handleSingleElementUnwrapped(JsonParser p, DeserializationContext ctxt) throws IOException {
            // not sure how this should work...
            return (char[]) ctxt.handleUnexpectedToken(_valueClass, p);
        }

        @Override
        protected char[] handleMapDeserialization(JsonParser p, DeserializationContext ctxt, String startKey) throws IOException {
            // Let's actually build as a String, then get chars
            TreeMap<Integer, Character> sortedMap = new TreeMap<>();
            for (int index = _indexForEntry(p, startKey); index != -2; index = _nextEntry(p)) {
                if (index == -1) continue;
                JsonToken t = p.currentToken();
                String str;
                if (t == JsonToken.VALUE_STRING) {
                    str = p.getText();
                } else if (t == JsonToken.VALUE_NULL) {
                    if (_nuller != null) {
                        _nuller.getNullValue(ctxt);
                        continue;
                    }
                    _verifyNullForPrimitive(ctxt);
                    str = "\0";
                } else {
                    CharSequence cs = (CharSequence) ctxt.handleUnexpectedToken(Character.TYPE, p);
                    str = cs.toString();
                }
                if (str.length() != 1) {
                    ctxt.reportInputMismatch(this, "Cannot convert a JSON String of length %d into a char element of char array", str.length());
                }
                sortedMap.put(index, str.charAt(0));
            }
            StringBuilder sb = new StringBuilder(64);
            for (Character value : sortedMap.values()) {
                sb.append(value);
            }
            return sb.toString().toCharArray();
        }

        @Override
        protected char[] _concat(char[] oldValue, char[] newValue) {
            int len1 = oldValue.length;
            int len2 = newValue.length;
            char[] result = Arrays.copyOf(oldValue, len1 + len2);
            System.arraycopy(newValue, 0, result, len1, len2);
            return result;
        }
    }

    /*
    /**********************************************************
    /* Actual deserializers: primivate array desers
    /**********************************************************
     */

    @JacksonStdImpl
    final static class BooleanDeser extends ModifiedPrimitiveArrayDeserializers<boolean[]> {
        private static final long serialVersionUID = 1L;

        public BooleanDeser() {
            super(boolean[].class);
        }

        protected BooleanDeser(ModifiedPrimitiveArrayDeserializers.BooleanDeser base, NullValueProvider nuller, Boolean unwrapSingle) {
            super(base, nuller, unwrapSingle);
        }

        @Override
        protected ModifiedPrimitiveArrayDeserializers<?> withResolved(NullValueProvider nuller, Boolean unwrapSingle) {
            return new ModifiedPrimitiveArrayDeserializers.BooleanDeser(this, nuller, unwrapSingle);
        }

        @Override
        protected boolean[] _constructEmpty() {
            return new boolean[0];
        }

        @Override
        public boolean[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (!p.isExpectedStartArrayToken()) {
                return handleNonArray(p, ctxt);
            }
            ArrayBuilders.BooleanBuilder builder = ctxt.getArrayBuilders().getBooleanBuilder();
            boolean[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                JsonToken t;
                while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                    boolean value;
                    if (t == JsonToken.VALUE_TRUE) {
                        value = true;
                    } else if (t == JsonToken.VALUE_FALSE) {
                        value = false;
                    } else if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                        _verifyNullForPrimitive(ctxt);
                        value = false;
                    } else {
                        value = _parseBooleanPrimitive(p, ctxt);
                    }
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected boolean[] handleSingleElementUnwrapped(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new boolean[]{_parseBooleanPrimitive(p, ctxt)};
        }

        @Override
        protected boolean[] handleMapDeserialization(JsonParser p, DeserializationContext ctxt, String startKey) throws IOException {
            TreeMap<Integer, Boolean> sortedMap = new TreeMap<>();
            ArrayBuilders.BooleanBuilder builder = ctxt.getArrayBuilders().getBooleanBuilder();
            boolean[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                for (int index = _indexForEntry(p, startKey); index != -2; index = _nextEntry(p)) {
                    if (index == -1) continue;
                    JsonToken t = p.currentToken();

                    boolean value;
                    if (t == JsonToken.VALUE_TRUE) {
                        value = true;
                    } else if (t == JsonToken.VALUE_FALSE) {
                        value = false;
                    } else if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                        _verifyNullForPrimitive(ctxt);
                        value = false;
                    } else {
                        value = _parseBooleanPrimitive(p, ctxt);
                    }
                    sortedMap.put(index, value);
                }
                // Build from sorted values
                for (Boolean value : sortedMap.values()) {
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected boolean[] _concat(boolean[] oldValue, boolean[] newValue) {
            int len1 = oldValue.length;
            int len2 = newValue.length;
            boolean[] result = Arrays.copyOf(oldValue, len1 + len2);
            System.arraycopy(newValue, 0, result, len1, len2);
            return result;
        }
    }

    /**
     * When dealing with byte arrays we have one more alternative (compared
     * to int/long/shorts): base64 encoded data.
     */
    @JacksonStdImpl
    final static class ByteDeser extends ModifiedPrimitiveArrayDeserializers<byte[]> {
        private static final long serialVersionUID = 1L;

        public ByteDeser() {
            super(byte[].class);
        }

        protected ByteDeser(ModifiedPrimitiveArrayDeserializers.ByteDeser base, NullValueProvider nuller, Boolean unwrapSingle) {
            super(base, nuller, unwrapSingle);
        }

        @Override
        protected ModifiedPrimitiveArrayDeserializers<?> withResolved(NullValueProvider nuller, Boolean unwrapSingle) {
            return new ModifiedPrimitiveArrayDeserializers.ByteDeser(this, nuller, unwrapSingle);
        }

        @Override
        protected byte[] _constructEmpty() {
            return new byte[0];
        }

        @Override // since 2.12
        public LogicalType logicalType() {
            // 30-May-2020, tatu: while technically an array, logically contains
            //    binary data so...
            return LogicalType.Binary;
        }

        @Override
        public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken t = p.currentToken();

            // Most likely case: base64 encoded String?
            if (t == JsonToken.VALUE_STRING) {
                try {
                    return p.getBinaryValue(ctxt.getBase64Variant());
                } catch (StreamReadException e) {
                    // 25-Nov-2016, tatu: related to [databind#1425], try to convert
                    //   to a more usable one, as it's not really a JSON-level parse
                    //   exception, but rather binding from JSON String into base64 decoded
                    //   binary data
                    String msg = e.getOriginalMessage();
                    if (msg.contains("base64")) {
                        return (byte[]) ctxt.handleWeirdStringValue(byte[].class, p.getText(), msg);
                    }
                }
            }
            // 31-Dec-2009, tatu: Also may be hidden as embedded Object
            if (t == JsonToken.VALUE_EMBEDDED_OBJECT) {
                Object ob = p.getEmbeddedObject();
                if (ob == null) return null;
                if (ob instanceof byte[]) {
                    return (byte[]) ob;
                }
            }
            if (!p.isExpectedStartArrayToken()) {
                return handleNonArray(p, ctxt);
            }
            ArrayBuilders.ByteBuilder builder = ctxt.getArrayBuilders().getByteBuilder();
            byte[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                    // whether we should allow truncating conversions?
                    byte value;
                    if (t == JsonToken.VALUE_NUMBER_INT) {
                        value = p.getByteValue(); // note: may throw due to overflow
                    } else {
                        // should probably accept nulls as 0
                        if (t == JsonToken.VALUE_NULL) {
                            if (_nuller != null) {
                                _nuller.getNullValue(ctxt);
                                continue;
                            }
                            _verifyNullForPrimitive(ctxt);
                            value = (byte) 0;
                        } else {
                            value = _parseBytePrimitive(p, ctxt);
                        }
                    }
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected byte[] handleSingleElementUnwrapped(JsonParser p, DeserializationContext ctxt) throws IOException {
            byte value;
            JsonToken t = p.currentToken();
            if (t == JsonToken.VALUE_NUMBER_INT) {
                value = p.getByteValue(); // note: may throw due to overflow
            } else {
                // should probably accept nulls as 'false'
                if (t == JsonToken.VALUE_NULL) {
                    if (_nuller != null) {
                        _nuller.getNullValue(ctxt);
                        return (byte[]) getEmptyValue(ctxt);
                    }
                    _verifyNullForPrimitive(ctxt);
                    return null;
                }
                Number n = (Number) ctxt.handleUnexpectedToken(_valueClass.getComponentType(), p);
                value = n.byteValue();
            }
            return new byte[]{value};
        }

        @Override
        protected byte[] handleMapDeserialization(JsonParser p, DeserializationContext ctxt, String startKey) throws IOException {
            TreeMap<Integer, Byte> sortedMap = new TreeMap<>();
            ArrayBuilders.ByteBuilder builder = ctxt.getArrayBuilders().getByteBuilder();
            byte[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                for (int index = _indexForEntry(p, startKey); index != -2; index = _nextEntry(p)) {
                    if (index == -1) continue;
                    JsonToken t = p.currentToken();

                    byte value;
                    if (t == JsonToken.VALUE_NUMBER_INT) {
                        value = p.getByteValue(); // note: may throw due to overflow
                    } else {
                        // should probably accept nulls as 0
                        if (t == JsonToken.VALUE_NULL) {
                            if (_nuller != null) {
                                _nuller.getNullValue(ctxt);
                                continue;
                            }
                            _verifyNullForPrimitive(ctxt);
                            value = (byte) 0;
                        } else {
                            value = _parseBytePrimitive(p, ctxt);
                        }
                    }
                    sortedMap.put(index, value);
                }
                // Build from sorted values
                for (Byte value : sortedMap.values()) {
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected byte[] _concat(byte[] oldValue, byte[] newValue) {
            int len1 = oldValue.length;
            int len2 = newValue.length;
            byte[] result = Arrays.copyOf(oldValue, len1 + len2);
            System.arraycopy(newValue, 0, result, len1, len2);
            return result;
        }
    }

    @JacksonStdImpl
    final static class ShortDeser extends ModifiedPrimitiveArrayDeserializers<short[]> {
        private static final long serialVersionUID = 1L;

        public ShortDeser() {
            super(short[].class);
        }

        protected ShortDeser(ModifiedPrimitiveArrayDeserializers.ShortDeser base, NullValueProvider nuller, Boolean unwrapSingle) {
            super(base, nuller, unwrapSingle);
        }

        @Override
        protected ModifiedPrimitiveArrayDeserializers<?> withResolved(NullValueProvider nuller, Boolean unwrapSingle) {
            return new ModifiedPrimitiveArrayDeserializers.ShortDeser(this, nuller, unwrapSingle);
        }

        @Override
        protected short[] _constructEmpty() {
            return new short[0];
        }

        @Override
        public short[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (!p.isExpectedStartArrayToken()) {
                return handleNonArray(p, ctxt);
            }
            ArrayBuilders.ShortBuilder builder = ctxt.getArrayBuilders().getShortBuilder();
            short[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                JsonToken t;
                while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                    short value;
                    if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                        _verifyNullForPrimitive(ctxt);
                        value = (short) 0;
                    } else {
                        value = _parseShortPrimitive(p, ctxt);
                    }
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected short[] handleSingleElementUnwrapped(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new short[]{_parseShortPrimitive(p, ctxt)};
        }

        @Override
        protected short[] handleMapDeserialization(JsonParser p, DeserializationContext ctxt, String startKey) throws IOException {
            TreeMap<Integer, Short> sortedMap = new TreeMap<>();
            ArrayBuilders.ShortBuilder builder = ctxt.getArrayBuilders().getShortBuilder();
            short[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                for (int index = _indexForEntry(p, startKey); index != -2; index = _nextEntry(p)) {
                    if (index == -1) continue;
                    JsonToken t = p.currentToken();

                    short value;
                    if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                        _verifyNullForPrimitive(ctxt);
                        value = (short) 0;
                    } else {
                        value = _parseShortPrimitive(p, ctxt);
                    }
                    sortedMap.put(index, value);
                }
                // Build from sorted values
                for (Short value : sortedMap.values()) {
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected short[] _concat(short[] oldValue, short[] newValue) {
            int len1 = oldValue.length;
            int len2 = newValue.length;
            short[] result = Arrays.copyOf(oldValue, len1 + len2);
            System.arraycopy(newValue, 0, result, len1, len2);
            return result;
        }
    }

    @JacksonStdImpl
    final static class IntDeser extends ModifiedPrimitiveArrayDeserializers<int[]> {
        private static final long serialVersionUID = 1L;

        public final static ModifiedPrimitiveArrayDeserializers.IntDeser instance = new ModifiedPrimitiveArrayDeserializers.IntDeser();

        public IntDeser() {
            super(int[].class);
        }

        protected IntDeser(ModifiedPrimitiveArrayDeserializers.IntDeser base, NullValueProvider nuller, Boolean unwrapSingle) {
            super(base, nuller, unwrapSingle);
        }

        @Override
        protected ModifiedPrimitiveArrayDeserializers<?> withResolved(NullValueProvider nuller, Boolean unwrapSingle) {
            return new ModifiedPrimitiveArrayDeserializers.IntDeser(this, nuller, unwrapSingle);
        }

        @Override
        protected int[] _constructEmpty() {
            return new int[0];
        }

        @Override
        public int[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (!p.isExpectedStartArrayToken()) {
                return handleNonArray(p, ctxt);
            }
            ArrayBuilders.IntBuilder builder = ctxt.getArrayBuilders().getIntBuilder();
            int[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                JsonToken t;
                while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                    int value;
                    if (t == JsonToken.VALUE_NUMBER_INT) {
                        value = p.getIntValue();
                    } else if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                        _verifyNullForPrimitive(ctxt);
                        value = 0;
                    } else {
                        value = _parseIntPrimitive(p, ctxt);
                    }
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected int[] handleSingleElementUnwrapped(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new int[]{_parseIntPrimitive(p, ctxt)};
        }

        @Override
        protected int[] handleMapDeserialization(JsonParser p, DeserializationContext ctxt, String startKey) throws IOException {
            TreeMap<Integer, Integer> sortedMap = new TreeMap<>();
            ArrayBuilders.IntBuilder builder = ctxt.getArrayBuilders().getIntBuilder();
            int[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                for (int index = _indexForEntry(p, startKey); index != -2; index = _nextEntry(p)) {
                    if (index == -1) continue;
                    JsonToken t = p.currentToken();
                    int value;
                    if (t == JsonToken.VALUE_NUMBER_INT) {
                        value = p.getIntValue();
                    } else if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                        _verifyNullForPrimitive(ctxt);
                        value = 0;
                    } else {
                        value = _parseIntPrimitive(p, ctxt);
                    }
                    sortedMap.put(index, value);
                }
                // Build from sorted values
                for (Integer value : sortedMap.values()) {
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected int[] _concat(int[] oldValue, int[] newValue) {
            int len1 = oldValue.length;
            int len2 = newValue.length;
            int[] result = Arrays.copyOf(oldValue, len1 + len2);
            System.arraycopy(newValue, 0, result, len1, len2);
            return result;
        }
    }

    @JacksonStdImpl
    final static class LongDeser extends ModifiedPrimitiveArrayDeserializers<long[]> {
        private static final long serialVersionUID = 1L;

        public final static ModifiedPrimitiveArrayDeserializers.LongDeser instance = new ModifiedPrimitiveArrayDeserializers.LongDeser();

        public LongDeser() {
            super(long[].class);
        }

        protected LongDeser(ModifiedPrimitiveArrayDeserializers.LongDeser base, NullValueProvider nuller, Boolean unwrapSingle) {
            super(base, nuller, unwrapSingle);
        }

        @Override
        protected ModifiedPrimitiveArrayDeserializers<?> withResolved(NullValueProvider nuller, Boolean unwrapSingle) {
            return new ModifiedPrimitiveArrayDeserializers.LongDeser(this, nuller, unwrapSingle);
        }

        @Override
        protected long[] _constructEmpty() {
            return new long[0];
        }

        @Override
        public long[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (!p.isExpectedStartArrayToken()) {
                return handleNonArray(p, ctxt);
            }
            ArrayBuilders.LongBuilder builder = ctxt.getArrayBuilders().getLongBuilder();
            long[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                JsonToken t;
                while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                    long value;
                    if (t == JsonToken.VALUE_NUMBER_INT) {
                        value = p.getLongValue();
                    } else if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                        _verifyNullForPrimitive(ctxt);
                        value = 0L;
                    } else {
                        value = _parseLongPrimitive(p, ctxt);
                    }
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected long[] handleSingleElementUnwrapped(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new long[]{_parseLongPrimitive(p, ctxt)};
        }

        @Override
        protected long[] handleMapDeserialization(JsonParser p, DeserializationContext ctxt, String startKey) throws IOException {
            TreeMap<Integer, Long> sortedMap = new TreeMap<>();
            ArrayBuilders.LongBuilder builder = ctxt.getArrayBuilders().getLongBuilder();
            long[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                for (int index = _indexForEntry(p, startKey); index != -2; index = _nextEntry(p)) {
                    if (index == -1) continue;
                    JsonToken t = p.currentToken();
                    long value;
                    if (t == JsonToken.VALUE_NUMBER_INT) {
                        value = p.getLongValue();
                    } else if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                        _verifyNullForPrimitive(ctxt);
                        value = 0;
                    } else {
                        value = _parseLongPrimitive(p, ctxt);
                    }
                    sortedMap.put(index, value);
                }
                // Build from sorted values
                for (Long value : sortedMap.values()) {
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected long[] _concat(long[] oldValue, long[] newValue) {
            int len1 = oldValue.length;
            int len2 = newValue.length;
            long[] result = Arrays.copyOf(oldValue, len1 + len2);
            System.arraycopy(newValue, 0, result, len1, len2);
            return result;
        }
    }

    @JacksonStdImpl
    final static class FloatDeser extends ModifiedPrimitiveArrayDeserializers<float[]> {
        private static final long serialVersionUID = 1L;

        public FloatDeser() {
            super(float[].class);
        }

        protected FloatDeser(ModifiedPrimitiveArrayDeserializers.FloatDeser base, NullValueProvider nuller, Boolean unwrapSingle) {
            super(base, nuller, unwrapSingle);
        }

        @Override
        protected ModifiedPrimitiveArrayDeserializers<?> withResolved(NullValueProvider nuller, Boolean unwrapSingle) {
            return new ModifiedPrimitiveArrayDeserializers.FloatDeser(this, nuller, unwrapSingle);
        }

        @Override
        protected float[] _constructEmpty() {
            return new float[0];
        }

        @Override
        public float[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (!p.isExpectedStartArrayToken()) {
                return handleNonArray(p, ctxt);
            }
            ArrayBuilders.FloatBuilder builder = ctxt.getArrayBuilders().getFloatBuilder();
            float[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                JsonToken t;
                while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                    // whether we should allow truncating conversions?
                    if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                    }
                    float value = _parseFloatPrimitive(p, ctxt);
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected float[] handleSingleElementUnwrapped(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new float[]{_parseFloatPrimitive(p, ctxt)};
        }

        @Override
        protected float[] handleMapDeserialization(JsonParser p, DeserializationContext ctxt, String startKey) throws IOException {
            TreeMap<Integer, Float> sortedMap = new TreeMap<>();
            ArrayBuilders.FloatBuilder builder = ctxt.getArrayBuilders().getFloatBuilder();
            float[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                for (int index = _indexForEntry(p, startKey); index != -2; index = _nextEntry(p)) {
                    if (index == -1) continue;
                    JsonToken t = p.currentToken();
                    if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                    }
                    float value = _parseFloatPrimitive(p, ctxt);
                    sortedMap.put(index, value);
                }
                // Build from sorted values
                for (Float value : sortedMap.values()) {
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected float[] _concat(float[] oldValue, float[] newValue) {
            int len1 = oldValue.length;
            int len2 = newValue.length;
            float[] result = Arrays.copyOf(oldValue, len1 + len2);
            System.arraycopy(newValue, 0, result, len1, len2);
            return result;
        }
    }

    @JacksonStdImpl
    final static class DoubleDeser extends ModifiedPrimitiveArrayDeserializers<double[]> {
        private static final long serialVersionUID = 1L;

        public DoubleDeser() {
            super(double[].class);
        }

        protected DoubleDeser(ModifiedPrimitiveArrayDeserializers.DoubleDeser base, NullValueProvider nuller, Boolean unwrapSingle) {
            super(base, nuller, unwrapSingle);
        }

        @Override
        protected ModifiedPrimitiveArrayDeserializers<?> withResolved(NullValueProvider nuller, Boolean unwrapSingle) {
            return new ModifiedPrimitiveArrayDeserializers.DoubleDeser(this, nuller, unwrapSingle);
        }

        @Override
        protected double[] _constructEmpty() {
            return new double[0];
        }

        @Override
        public double[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (!p.isExpectedStartArrayToken()) {
                return handleNonArray(p, ctxt);
            }
            ArrayBuilders.DoubleBuilder builder = ctxt.getArrayBuilders().getDoubleBuilder();
            double[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                JsonToken t;
                while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                    if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                    }
                    double value = _parseDoublePrimitive(p, ctxt);
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected double[] handleSingleElementUnwrapped(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new double[]{_parseDoublePrimitive(p, ctxt)};
        }

        @Override
        protected double[] handleMapDeserialization(JsonParser p, DeserializationContext ctxt, String startKey) throws IOException {
            TreeMap<Integer, Double> sortedMap = new TreeMap<>();
            ArrayBuilders.DoubleBuilder builder = ctxt.getArrayBuilders().getDoubleBuilder();
            double[] chunk = builder.resetAndStart();
            int ix = 0;

            try {
                for (int index = _indexForEntry(p, startKey); index != -2; index = _nextEntry(p)) {
                    if (index == -1) continue;
                    JsonToken t = p.currentToken();
                    if (t == JsonToken.VALUE_NULL) {
                        if (_nuller != null) {
                            _nuller.getNullValue(ctxt);
                            continue;
                        }
                    }
                    double value = _parseDoublePrimitive(p, ctxt);
                    sortedMap.put(index, value);
                }
                // Build from sorted values
                for (Double value : sortedMap.values()) {
                    if (ix >= chunk.length) {
                        chunk = builder.appendCompletedChunk(chunk, ix);
                        ix = 0;
                    }
                    chunk[ix++] = value;
                }
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, chunk, builder.bufferedSize() + ix);
            }
            return builder.completeAndClearBuffer(chunk, ix);
        }

        @Override
        protected double[] _concat(double[] oldValue, double[] newValue) {
            int len1 = oldValue.length;
            int len2 = newValue.length;
            double[] result = Arrays.copyOf(oldValue, len1 + len2);
            System.arraycopy(newValue, 0, result, len1, len2);
            return result;
        }
    }
}
