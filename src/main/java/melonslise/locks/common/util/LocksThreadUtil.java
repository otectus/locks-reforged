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
	private static final AtomicBoolean NULL_SERVER_LOGGED = new AtomicBoolean(false);

	// Captured the first time we see a live server, so the server==null branch can still tell whether it is
	// executing on the owning thread without a server handle (very early load / teardown edge cases).
	private static volatile Thread serverThread;

	private LocksThreadUtil() {}

	/**
	 * Runs {@code work} on the main server thread. If already on the server thread (the normal case), it runs
	 * immediately; otherwise it is scheduled for the next tick and a one-time compatibility notice is logged.
	 *
	 * <p><b>CONTRACT — {@code work} MUST be non-blocking.</b> In particular it must NEVER perform a blocking
	 * chunk fetch ({@link net.minecraft.world.level.Level#getChunk(int, int)}, {@code Level#getChunkAt}, or any
	 * {@code getChunk(..., /*load*}{@code / true)}). Under async chunk mods (C2ME) this method can run
	 * {@code work} INLINE and RE-ENTRANTLY on the main thread while that thread is draining the chunk executor
	 * (BlockableEventLoop#managedBlock) to complete a chunk future. A blocking chunk fetch from inside such a
	 * drain re-parks the only thread that could complete the future -> permanent hang. Use the non-blocking
	 * {@link net.minecraft.world.level.Level#hasChunk(int, int)} / {@code getChunkNow} plus a chunk reference you
	 * already hold.
	 */
	public static void runOnServerThread(MinecraftServer server, Runnable work)
	{
		if(server != null)
		{
			serverThread = server.getRunningThread();
			if(server.isSameThread())
			{
				work.run();
				return;
			}
			if(COMPAT_LOGGED.compareAndSet(false, true))
				Locks.LOGGER.info("Detected off-thread chunk/structure operation (e.g. C2ME). Deferring lockable handler updates and packet sync to the main server thread. This message is logged once.");
			server.tell(new TickTask(server.getTickCount() + 1, work));
			return;
		}
		// No server handle: we cannot schedule onto the main thread. Mutating the world-global handler off the
		// owning thread is the exact corruption this helper exists to prevent, so only run inline when we can prove
		// we are on (or have never yet identified) the owning thread; otherwise DROP the work. Dropped updates
		// self-heal on the next chunk (re)load or interaction.
		Thread owner = serverThread;
		if(owner == null || Thread.currentThread() == owner)
		{
			work.run();
			return;
		}
		if(NULL_SERVER_LOGGED.compareAndSet(false, true))
			Locks.LOGGER.warn("Skipping off-thread lockable handler update with no server available to defer to; state will self-heal on next chunk (re)load or interaction. This message is logged once.");
	}
}
