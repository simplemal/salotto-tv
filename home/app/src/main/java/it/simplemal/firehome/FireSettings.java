package it.simplemal.firehome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Video and audio settings, through the services of Amazon's settings app: Fire OS keeps these
 * behind permissions an app cannot have, and those two services are open to anyone. Their calls
 * block, so everything here runs off the main thread. Layout worked out from Amazon's code.
 */
final class FireSettings {
    static final String PICTURE = "PictureSettingsProvider";
    static final String AUDIO = "AudioSettingsProvider";

    private static final String PACKAGE = "com.amazon.tv.settings.v2";
    private static final String PROVIDERS = PACKAGE + ".provider.";
    private static final String TOKEN = "com.amazon.tv.settingsframework.provider.ISettingsProviderService";
    private static final int GET = 1;
    private static final int SET = 3;
    private static final int TYPE_STRING = 0;
    private static final int TYPE_TYPED_LIST = 12;
    private static final int STATUS_SUCCESS = 0;
    private static final long BIND_WAIT_S = 5;
    /** The service connects to its own core after starting, and fails until then. */
    private static final int WRITE_TRIES = 6;
    private static final long WRITE_RETRY_MS = 500;

    private final Context context;
    private final Map<String, IBinder> binders = new ConcurrentHashMap<>();
    private final Map<String, ServiceConnection> connections = new ConcurrentHashMap<>();
    private volatile boolean closed;

    FireSettings(Context context) { this.context = context.getApplicationContext(); }

    /** Whoever takes the connection out of the map undoes it, so that this happens only once. */
    private void disconnect(String provider) {
        binders.remove(provider);
        ServiceConnection connection = connections.remove(provider);
        if (connection == null) return;
        try {
            context.unbindService(connection);
        } catch (IllegalArgumentException e) {
            // It never got as far as a binding: nothing is left to undo.
        }
    }

    private synchronized IBinder connect(String provider) {
        if (closed) return null;
        IBinder known = binders.get(provider);
        if (known != null && known.isBinderAlive()) return known;
        // A service that died or never answered still holds its binding.
        disconnect(provider);
        CountDownLatch bound = new CountDownLatch(1);
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                binders.put(provider, service);
                bound.countDown();
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                binders.remove(provider);
            }
        };
        Intent intent = new Intent().setClassName(PACKAGE, PROVIDERS + provider);
        try {
            boolean binding = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            // Recorded after the bind, so that close() cannot pass between the two and miss it.
            connections.put(provider, connection);
            if (!binding) {
                disconnect(provider);
                return null;
            }
            bound.await(BIND_WAIT_S, TimeUnit.SECONDS);
        } catch (SecurityException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Settings may have closed during the wait, and close() does not wait for this.
        if (closed) {
            disconnect(provider);
            return null;
        }
        return binders.get(provider);
    }

    private static void writeText(Parcel data, String text) {
        data.writeInt(TYPE_STRING);
        data.writeInt(1);
        data.writeString(text);
    }

    private static void startRequest(Parcel data) {
        data.writeInterfaceToken(TOKEN);
        data.writeInt(1);
        data.writeInt(1);
        data.writeInt(1);
        data.writeInt(1);
    }

    /** Past the header of the reply: false when it holds no item. */
    private static boolean openReply(Parcel reply) {
        reply.readException();
        reply.readInt();
        if (reply.readInt() < 1) return false;
        reply.readInt();
        reply.readInt();
        return true;
    }

    private static String readText(Parcel reply) {
        reply.readInt();
        return reply.readInt() == 1 ? reply.readString() : null;
    }

    /** The current value, or null when the service does not answer. */
    String read(String provider, String id) {
        IBinder binder = connect(provider);
        if (binder == null) return null;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            startRequest(data);
            writeText(data, id);
            data.writeInt(TYPE_TYPED_LIST);
            data.writeInt(0);
            data.writeInt(-1);
            binder.transact(GET, data, reply, 0);
            if (!openReply(reply)) return null;
            readText(reply);
            return readText(reply);
        } catch (RemoteException | RuntimeException e) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private boolean writeOnce(IBinder binder, String id, String action) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            startRequest(data);
            writeText(data, id);
            writeText(data, action);
            data.writeInt(-1);
            binder.transact(SET, data, reply, 0);
            if (!openReply(reply)) return false;
            readText(reply);
            reply.readInt();
            reply.readInt();
            return reply.readInt() == STATUS_SUCCESS;
        } catch (RemoteException | RuntimeException e) {
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    boolean write(String provider, String id, String action) {
        IBinder binder = connect(provider);
        if (binder == null) return false;
        for (int attempt = 0; attempt < WRITE_TRIES; attempt++) {
            if (writeOnce(binder, id, action)) return true;
            try {
                Thread.sleep(WRITE_RETRY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    void close() {
        closed = true;
        for (String provider : connections.keySet()) disconnect(provider);
    }
}
