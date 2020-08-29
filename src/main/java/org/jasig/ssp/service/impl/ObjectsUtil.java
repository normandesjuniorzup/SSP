package org.jasig.ssp.service.impl;

public class ObjectsUtil {

    public static <E extends Exception> void checkNotNull(Object object, E e) throws E {
        //1
        if (object == null) {
            throw e;
        }
    }

}
