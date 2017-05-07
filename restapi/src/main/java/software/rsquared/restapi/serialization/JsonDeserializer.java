package software.rsquared.restapi.serialization;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import software.rsquared.restapi.exceptions.DeserializationException;

/**
 * Default implementation of response {@link Deserializer deserializer}
 *
 * @author Rafał Zajfert
 */
public class JsonDeserializer implements Deserializer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Config config;

    public JsonDeserializer() {
        this(new Config());
    }

    public JsonDeserializer(@NonNull Config config) {
        this.config = config;
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        SimpleModule module = new SimpleModule();
        setupModule(module);
        objectMapper.registerModule(module);
    }

    @CallSuper
    protected void setupModule(SimpleModule module) {
        if (config.timeInSeconds) {
            module.addDeserializer(Calendar.class, new com.fasterxml.jackson.databind.JsonDeserializer<Calendar>() {
                @Override
                public Calendar deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
                    long value = p.getLongValue();
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(value * 1000);
                    return calendar;
                }
            });
            module.addDeserializer(Date.class, new com.fasterxml.jackson.databind.JsonDeserializer<Date>() {
                @Override
                public Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
                    long value = p.getLongValue();
                    return new Date(value * 1000);
                }
            });
        }
    }

    @Override
    public <T> T read(Class<?> requestClass, String content) throws IOException {
        Type superclass = requestClass.getGenericSuperclass();
        while (!(superclass instanceof ParameterizedType) && requestClass.getSuperclass()!=null){
            requestClass = requestClass.getSuperclass();
            superclass = requestClass.getGenericSuperclass();
        }
        if (superclass!= null && superclass instanceof ParameterizedType){
            return readObject(getParameterClasses((ParameterizedType) superclass), content);
        }else{
            throw new DeserializationException("Unknown parameter response class for " + requestClass.getSimpleName());
        }

    }

    private  <T> T readObject(List<Class<?>> classes, String content) throws IOException {
        int classesCount = classes.size();
        if (TextUtils.isEmpty(content)){
            content = getEmptyJson(classes.get(0));
        }
        if (classesCount > 1) {
            JavaType javaType = null;
            for (int i = classesCount - 1; i >= 1; i--) {
                if (javaType == null) {
                    javaType = objectMapper.getTypeFactory().constructParametricType(classes.get(i - 1), classes.get(i));
                } else {
                    javaType = objectMapper.getTypeFactory().constructParametricType(classes.get(i - 1), javaType);
                }
            }
            return objectMapper.readerFor(javaType).readValue(content);
        } else {

            return objectMapper.readerFor(classes.get(0)).readValue(content);
        }
    }

    private String getEmptyJson(@NonNull Class<?> clazz){
        if (isArray(clazz)){
            return "[]";
        }else{
            return "{}";
        }
    }

    private boolean isArray(@NonNull Class<?> clazz) {
        return Collection.class.isAssignableFrom(clazz) || clazz.isArray();
    }

    public static List<Class<?>> getParameterClasses(@NonNull ParameterizedType type) {
        List<Class<?>> classes = new ArrayList<>();
        Type subType = type.getActualTypeArguments()[0];
        if (subType instanceof Class) {
            classes.add((Class<?>) subType);
        } else if (subType instanceof ParameterizedType) {
            classes.add((Class<?>) ((ParameterizedType) subType).getRawType());
            classes.addAll(getParameterClasses((ParameterizedType) subType));
        }
        return classes;
    }

    public static class Config {
        private boolean timeInSeconds;

        /**
         * Set true if time should be serialized to unix time seconds
         */
        public Config setTimeInSeconds(boolean timeInSeconds) {
            this.timeInSeconds = timeInSeconds;
            return this;
        }

    }
}
