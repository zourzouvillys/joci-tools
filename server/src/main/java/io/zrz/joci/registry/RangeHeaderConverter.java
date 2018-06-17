package io.zrz.joci.registry;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.inject.Singleton;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

@Provider
@Singleton
public class RangeHeaderConverter implements ParamConverterProvider {

  @Override
  public <T> ParamConverter<T> getConverter(final Class<T> klass, final Type genericType, final Annotation[] annotations) {
    if (klass.getName().equals(RangeHeader.class.getName())) {

      return new ParamConverter<T>() {

        @SuppressWarnings("unchecked")
        @Override
        public T fromString(final String value) {
          return (T) RangeHeader.fromValue(value);
        }

        @Override
        public String toString(final T value) {
          return value.toString();
        }

      };

    }
    return null;
  }

}
