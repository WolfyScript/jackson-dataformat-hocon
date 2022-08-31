package com.wolfyscript.jackson.dataformat.hocon;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.wolfyscript.jackson.dataformat.hocon.deserialization.HoconBeanDeserializerModifier;
import com.wolfyscript.jackson.dataformat.hocon.deserialization.ModifiedPrimitiveArrayDeserializers;
import com.wolfyscript.jackson.dataformat.hocon.deserialization.ModifiedStringArrayDeserializer;

public class HoconMapper extends ObjectMapper {

    private static final Class<?>[] PRIMITIVE_ARRAY_TYPES = new Class[] { boolean[].class, byte[].class, char[].class, double[].class, float[].class, int[].class, long[].class, short[].class };

    public HoconMapper() {
        this(new HoconFactory());
    }

    public HoconMapper(HoconFactory hoconFactory) {
        super(hoconFactory);
        initHoconModul();
    }

    private void initHoconModul() {
        SimpleModule module = new SimpleModule();
        module.setDeserializerModifier(new HoconBeanDeserializerModifier());
        module.addDeserializer(String[].class, ModifiedStringArrayDeserializer.instance);
        for (Class<?> primitiveArrayType : PRIMITIVE_ARRAY_TYPES) {
            addPrimitiveArrayDeserializer(module, primitiveArrayType);
        }
        registerModule(module);
    }

    private <T> void addPrimitiveArrayDeserializer(SimpleModule module, Class<T> arrayType) {
        module.addDeserializer(arrayType, (JsonDeserializer<? extends T>) ModifiedPrimitiveArrayDeserializers.forType(arrayType.getComponentType()));
    }


}
