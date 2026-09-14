import com.kiyori.platform.network.MihomoProcessLifetimeKt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Linux-only integration with the compiled production launcher and Kotlin lifetime owner.
 * Compile after :app:compileDebugKotlin, using the app classes and kotlin-stdlib on the classpath:
 * javac -cp "$APP_CLASSES:$KOTLIN_STDLIB" -d "$TEST_CLASSES" LifetimeIntegrationTest.java
 * gcc -x c native-lib.cpp -o "$TEST_CLASSES/launcher"
 * java -Djdk.lang.Process.launchMechanism=FORK -cp "$TEST_CLASSES:$APP_CLASSES:$KOTLIN_STDLIB"
 *      LifetimeIntegrationTest "$TEST_CLASSES/launcher"
 * Repeat with POSIX_SPAWN. No Android device or external endpoint is contacted.
 */
public final class LifetimeIntegrationTest {
    private static Process launch(String launcher) {
        try {
            return new ProcessBuilder(launcher, "/bin/sleep", "30").start();
        } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[1].equals("host")) {
            Process child = MihomoProcessLifetimeKt.startMihomoParentBoundProcess(() -> launch(args[0]));
            System.out.println(child.pid());
            System.out.flush();
            Thread.sleep(30_000);
            return;
        }
        for (boolean retainCreator : new boolean[] {false, true}) {
            AtomicReference<Process> process = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                try {
                    process.set(retainCreator
                        ? MihomoProcessLifetimeKt.startMihomoParentBoundProcess(() -> launch(args[0]))
                        : launch(args[0]));
                    // Let exec install PDEATHSIG before deliberately retiring the caller.
                    Thread.sleep(300);
                } catch (Throwable error) {
                    failure.set(error);
                }
            });
            caller.start();
            caller.join(3000);
            if (caller.isAlive() || failure.get() != null || process.get() == null) {
                throw new AssertionError("startup failed", failure.get());
            }
            Process child = process.get();
            try {
                boolean exited = child.waitFor(700, TimeUnit.MILLISECONDS);
                if (exited == retainCreator) {
                    throw new AssertionError("wrong child lifetime retainCreator=" + retainCreator);
                }
                if (!retainCreator && child.exitValue() != 143) {
                    throw new AssertionError("expected SIGTERM: " + child.exitValue());
                }
                System.out.println("PASS caller retired, retainCreator=" + retainCreator);
            } finally {
                child.destroyForcibly();
                child.waitFor(3, TimeUnit.SECONDS);
            }
        }

        Process host = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Djdk.lang.Process.launchMechanism=" + System.getProperty("jdk.lang.Process.launchMechanism", "POSIX_SPAWN"),
            "-cp", System.getProperty("java.class.path"), LifetimeIntegrationTest.class.getName(), args[0], "host"
        ).start();
        long childPid = -1;
        try {
            childPid = Long.parseLong(host.inputReader().readLine());
            Thread.sleep(300);
            host.destroyForcibly();
            if (!host.waitFor(3, TimeUnit.SECONDS)) throw new AssertionError("host did not exit");
            boolean stopped = false;
            for (int i = 0; i < 60; i++) {
                Path stat = Path.of("/proc", Long.toString(childPid), "stat");
                if (!Files.exists(stat)) { stopped = true; break; }
                String value;
                try { value = Files.readString(stat); }
                catch (java.nio.file.NoSuchFileException gone) { stopped = true; break; }
                if (value.substring(value.lastIndexOf(')') + 2).startsWith("Z ")) { stopped = true; break; }
                Thread.sleep(50);
            }
            if (!stopped) throw new AssertionError("host death left the core running");
            System.out.println("PASS host death still terminates child");
        } finally {
            host.destroyForcibly();
            if (childPid > 0) ProcessHandle.of(childPid).ifPresent(ProcessHandle::destroyForcibly);
        }
    }
}
