package io.github.sospd.engine;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;
import com.watabou.utils.GameSettings;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Only supplies platform services. No game lifecycle, rendering loop or GL context is created. */
final class HeadlessRuntime {
    private static boolean initialized;

    static synchronized void initialize(File storage) {
        if (initialized) return;
        storage.mkdirs();
        Preferences preferences = new MemoryPreferences();
        GameSettings.set(preferences);
        SPDSettings.language(Languages.CHI_SMPL);
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                new Class<?>[]{Application.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPreferences" -> preferences;
                    case "getType" -> Application.ApplicationType.HeadlessDesktop;
                    case "getVersion" -> 0;
                    case "getJavaHeap", "getNativeHeap" -> 0L;
                    case "postRunnable" -> { ((Runnable) args[0]).run(); yield null; }
                    case "log", "debug", "error" -> {
                        if (args != null && args.length > 1 && method.getName().equals("error")) {
                            System.err.println(args[0] + ": " + args[1]);
                        }
                        yield null;
                    }
                    case "getLogLevel" -> Application.LOG_ERROR;
                    case "toString" -> "SoSPD headless runtime";
                    default -> defaultValue(method.getReturnType());
                });
        Gdx.files = (Files) Proxy.newProxyInstance(Files.class.getClassLoader(),
                new Class<?>[]{Files.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "internal", "classpath" -> new ResourceHandle((String) args[0]);
                    case "local", "external" -> new FileHandle(new File(storage, (String) args[0]));
                    case "absolute" -> new FileHandle((String) args[0]);
                    case "getFileHandle" -> args[1] == Files.FileType.Internal || args[1] == Files.FileType.Classpath
                            ? new ResourceHandle((String) args[0]) : new FileHandle(new File(storage, (String) args[0]));
                    case "getLocalStoragePath", "getExternalStoragePath" -> storage.getAbsolutePath() + File.separator;
                    case "isLocalStorageAvailable", "isExternalStorageAvailable" -> true;
                    default -> defaultValue(method.getReturnType());
                });
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
                new Class<?>[]{Input.class}, (p, m, a) -> defaultValue(m.getReturnType()));
        FileUtils.setDefaultFileProperties(Files.FileType.Local, "");
        // Some item descriptions query Game.scene(). The holder has no scene and create() is never invoked.
        new Game(null, null);
        Game.version = "4.0.2";
        Game.versionCode = 922;
        Game.realTime = System.currentTimeMillis();
        initialized = true;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private static final class ResourceHandle extends FileHandle {
        ResourceHandle(String path) { super(path, Files.FileType.Classpath); }
        @Override public InputStream read() {
            InputStream stream = HeadlessRuntime.class.getClassLoader().getResourceAsStream(path());
            if (stream == null) throw new IllegalStateException("缺少中文资源：" + path());
            return stream;
        }
        @Override public boolean exists() {
            return HeadlessRuntime.class.getClassLoader().getResource(path()) != null;
        }
        @Override public FileHandle sibling(String name) {
            String path = path();
            return new ResourceHandle(path.substring(0, path.lastIndexOf('/') + 1) + name);
        }
    }

    private static final class MemoryPreferences implements Preferences {
        private final Map<String, Object> values = new HashMap<>();
        public Preferences putBoolean(String k, boolean v) { values.put(k, v); return this; }
        public Preferences putInteger(String k, int v) { values.put(k, v); return this; }
        public Preferences putLong(String k, long v) { values.put(k, v); return this; }
        public Preferences putFloat(String k, float v) { values.put(k, v); return this; }
        public Preferences putString(String k, String v) { values.put(k, v); return this; }
        public Preferences put(Map<String, ?> v) { values.putAll(v); return this; }
        public boolean getBoolean(String k) { return getBoolean(k, false); }
        public int getInteger(String k) { return getInteger(k, 0); }
        public long getLong(String k) { return getLong(k, 0L); }
        public float getFloat(String k) { return getFloat(k, 0f); }
        public String getString(String k) { return getString(k, ""); }
        public boolean getBoolean(String k, boolean d) { return (boolean) values.getOrDefault(k, d); }
        public int getInteger(String k, int d) { return (int) values.getOrDefault(k, d); }
        public long getLong(String k, long d) { return (long) values.getOrDefault(k, d); }
        public float getFloat(String k, float d) { return (float) values.getOrDefault(k, d); }
        public String getString(String k, String d) { return (String) values.getOrDefault(k, d); }
        public Map<String, ?> get() { return new HashMap<>(values); }
        public boolean contains(String k) { return values.containsKey(k); }
        public void clear() { values.clear(); }
        public void remove(String k) { values.remove(k); }
        public void flush() { }
    }
}

