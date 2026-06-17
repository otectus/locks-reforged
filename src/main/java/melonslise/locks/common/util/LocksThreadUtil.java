package melonslise.locks.common.util;

import java.util.concurrent.atomic.AtomicBoolean;

import melonslise.locks.Locks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;

/**
 * Helpers for keeping lockable handler mutation and network sync on the main server thread.
 *
 * <p>Without async chunk mods (e.g. C2ME) the chunk/structure code paths that touch the world-global
 * {@code LockableHandler} run on the main server thread, so {@link #runOnServerThread} executes the work
 * inline and behavior is unchanged. Under C2ME those paths run on parallel worker threads, where mutating
 * the non-thread-safe handler map or sending packets corrupts state (issue #10:
 * ArrayIndexOutOfBoundsException in LevelChunk). In that case the work is deferred to the next server tick
 * on the main thread, restoring the single-threaded invariant the rest of the mod assumes.
 */
public final class LocksThreadUtil
{
	private static final AtomicBoolean COMPAT_LOGGED = new AtomicBoolean(false);

	private LocksThreadUtil() {}

	/**
	 * Runs {@code work} on the main server thread. If already on the server thread (the normal case),
	 * it runs immediately. Otherwise it is scheduled for the next tick and a one-time compatibility
	 * notice is logged. If {@code server} is null the work runs inline as a best effort.
	 */
	public static void runOnServerThread(MinecraftServer server, Runnable work)
	{
		if(server == null || server.isSameThread())
		{
			work.run();
			return;
		}
		if(COMPAT_LOGGED.compareAndSet(false, true))
			Locks.LOGGER.info("Detected off-thread chunk/structure operation (e.g. C2ME). Deferring lockable handler updates and packet sync to the main server thread. This message is logged once.");
		server.tell(new TickTask(server.getTickCount() + 1, work));
	}
}
