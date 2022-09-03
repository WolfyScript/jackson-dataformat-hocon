package com.wolfyscript.jackson.dataformat.hocon.deserialization;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.CollectionDeserializer;
import com.fasterxml.jackson.databind.deser.std.ObjectArrayDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer;
import com.fasterxml.jackson.databind.type.ArrayType;
import com.fasterxml.jackson.databind.type.CollectionLikeType;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;

public class HoconBeanDeserializerModifier extends BeanDeserializerModifier {

    @Override
    public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
        return super.modifyDeserializer(config, beanDesc, deserializer);
    }

    @Override
    public JsonDeserializer<?> modifyMapDeserializer(DeserializationConfig config, MapType type, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
        return super.modifyMapDeserializer(config, type, beanDesc, deserializer);
    }

    @Override
    public JsonDeserializer<?> modifyCollectionDeserializer(DeserializationConfig config, CollectionType type, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
        if (deserializer instanceof CollectionDeserializer) {
            CollectionDeserializer collectionDeserializer = (CollectionDeserializer) deserializer;
            return new ModifiedCollectionDeserializer(collectionDeserializer);
        } else if (deserializer instanceof StringCollectionDeserializer) {
            StringCollectionDeserializer collectionDeserializer = (StringCollectionDeserializer) deserializer;
            return new ModifiedStringCollectionDeserializer(collectionDeserializer.getValueType(), collectionDeserializer.getContentDeserializer(), collectionDeserializer.getValueInstantiator());
        }
        return super.modifyCollectionDeserializer(config, type, beanDesc, deserializer);
    }

    @Override
    public JsonDeserializer<?> modifyCollectionLikeDeserializer(DeserializationConfig config, CollectionLikeType type, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
        return super.modifyCollectionLikeDeserializer(config, type, beanDesc, deserializer);
    }

    @Override
    public JsonDeserializer<?> modifyArrayDeserializer(DeserializationConfig config, ArrayType valueType, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
        try {
            if (deserializer instanceof ObjectArrayDeserializer) {
                return new ModifiedObjectArrayDeserializer(valueType, ((ObjectArrayDeserializer) deserializer).getContentDeserializer(), config.findTypeDeserializer(valueType));
            }
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        }
        return super.modifyArrayDeserializer(config, valueType, beanDesc, deserializer);
    }
}
