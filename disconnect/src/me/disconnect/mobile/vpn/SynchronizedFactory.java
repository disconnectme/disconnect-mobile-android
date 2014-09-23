package me.disconnect.mobile.vpn;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Factory class capable of producing synchronized wrapper for arbitrary objects.
 * From http://stackoverflow.com/questions/743288/java-synchronization-utility
 * @author Chris Jester-Young, http://about.me/cky
 */
public final class SynchronizedFactory {
    private SynchronizedFactory() {} // Private constructor guarantees no instances get created.

    /**
     * Utility that can take any object that implements a given interface and returns
     * a proxy that implements the same interface and synchronizes all calls that are
     * delegated to the given object. From Chris Jester-Young, http://about.me/cky
     * @param interfaceClass The interface to synchronize. Use MyInterface.class.
     * @param object The object to synchronize that implements the given interface class.
     * @return A synchronized proxy object that delegates to the given object.
     */
    public static <T> T makeSynchronized(Class<T> interfaceClass, final T object) {
        return interfaceClass.cast(
            Proxy.newProxyInstance(
                object.getClass().getClassLoader(),
                new Class<?>[]{interfaceClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        synchronized (object) {
                            return method.invoke(object, args);
                        }
                    }
                }
            )
        );
    }
}
