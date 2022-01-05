package reserve.lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A Read Write lock where the write lock can be reserved allowing the reader to continue but
 * preventing other threads from acquiring the write lock thus allowing the reserve thread to
 * upgrade to a Writer.
 *
 * @author omilton
 * @see ReserveWriteReadWriteLock
 */
public class ReentrantReserveWriteReadWriteLock implements ReserveWriteReadWriteLock {

  private final ReentrantReadWriteLock readWriteDelegate;
  private final ReentrantLock reserveDelegate;
  private final WriteLock writeLock = new WriteLock();
  private final ReadLock readLock = new ReadLock();
  private final ReserveWriteLock reserveLock = new ReserveWriteLock();

  public ReentrantReserveWriteReadWriteLock() {
    this(false);
  }

  public ReentrantReserveWriteReadWriteLock(boolean fair) {
    readWriteDelegate = new ReentrantReadWriteLock(fair);
    reserveDelegate = new ReentrantLock(fair);
  }

  @Override
  public Lock readLock() {
    return readLock;
  }

  @Override
  public Lock writeLock() {
    return writeLock;
  }

  @Override
  public Lock reserveLock() {
    return reserveLock;
  }

  private final class ReadLock implements Lock {

    /**
     * Acquires the read lock.
     *
     * <p>Acquires the read lock if the write lock is not held by another thread and returns
     * immediately.
     *
     * <p>If the write lock is held by another thread then the current thread becomes disabled for
     * thread scheduling purposes and lies dormant until the read lock has been acquired.
     */
    @Override
    public void lock() {
      readWriteDelegate.readLock().lock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
      readWriteDelegate.readLock().lockInterruptibly();
    }

    @Override
    public boolean tryLock() {
      return readWriteDelegate.readLock().tryLock();
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
      return readWriteDelegate.readLock().tryLock(time, unit);
    }

    @Override
    public void unlock() {
      readWriteDelegate.readLock().unlock();
    }

    @Override
    public Condition newCondition() {
      return readWriteDelegate.readLock().newCondition();
    }
  }

  private final class ReserveWriteLock implements Lock {

    /**
     * Reserves the write lock so that only the calling thread may acquire it. Attempts made by
     * other threads to acquire the write lock will be queued against the reservation lock. Readers
     * are can continue and the holder of the reservation lock can also read.
     *
     * @return The lock used to reserve the write lock.
     */
    @Override
    public void lock() {
      validate();
      reserveDelegate.lock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
      validate();
      reserveDelegate.lockInterruptibly();
    }

    @Override
    public boolean tryLock() {
      validate();
      return reserveDelegate.tryLock();
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
      validate();
      return reserveDelegate.tryLock(time, unit);
    }

    @Override
    public void unlock() {
      if (readWriteDelegate.isWriteLockedByCurrentThread()) {
        throw new IllegalStateException(
            "Cannot release the reserve write lock while holding the write lock.");
      }
      reserveDelegate.unlock();
    }

    @Override
    public Condition newCondition() {
      return reserveDelegate.newCondition();
    }

    private void validate() {
      if (readWriteDelegate.getReadHoldCount() > 0) {
        throw new IllegalStateException(
            "Cannot acquire reserve write lock while holding the read lock.");
      }
    }
  }

  private final class WriteLock implements Lock {

    /**
     * Acquires the write lock.
     *
     * <p>Acquires the write lock if the reservation lock is not held by another thread and returns
     * immediately. If the reservation lock is not held by the calling thread then is is also
     * acquired.
     *
     * <p>If the reservation lock is held by another thread then the calling thread becomes disabled
     * for thread scheduling purposes and lies dormant until the reservation lock has been acquired.
     */
    @Override
    public void lock() {
      validate();
      reserveDelegate.lock();
      try {
        readWriteDelegate.writeLock().lock();
      } catch (RuntimeException e) {
        reserveDelegate.unlock();
        throw e;
      }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
      validate();
      reserveDelegate.lockInterruptibly();
      try {
        readWriteDelegate.writeLock().lockInterruptibly();
      } catch (InterruptedException | RuntimeException e) {
        reserveDelegate.unlock();
        throw e;
      }
    }

    @Override
    public boolean tryLock() {
      validate();
      boolean res = reserveDelegate.tryLock();
      try {
        return res && readWriteDelegate.writeLock().tryLock();
      } catch (RuntimeException e) {
        reserveDelegate.unlock();
        throw e;
      }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
      validate();
      boolean res = reserveDelegate.tryLock(time, unit);
      try {
        return res && readWriteDelegate.writeLock().tryLock(time, unit);
      } catch (InterruptedException | RuntimeException e) {
        reserveDelegate.unlock();
        throw e;
      }
    }

    @Override
    public void unlock() {
      readWriteDelegate.writeLock().unlock();
      reserveDelegate.unlock();
    }

    @Override
    public Condition newCondition() {
      return readWriteDelegate.writeLock().newCondition();
    }

    private void validate() {
      if (readWriteDelegate.getReadHoldCount() > 0) {
        throw new IllegalStateException("Cannot acquire write lock while holding the read lock.");
      }
    }
  }

  /**
   * Queries the number of read locks held for this lock. This method is designed for use in
   * monitoring system state, not for synchronisation control.
   *
   * @return the number of read locks held
   */
  public int getReadLockCount() {
    return readWriteDelegate.getReadLockCount();
  }

  /**
   * Queries if the write lock is held by any thread. This method is designed for use in monitoring
   * system state, not for synchronisation control.
   *
   * @return {@code true} if any thread holds the write lock and {@code false} otherwise
   */
  public boolean isWriteLocked() {
    return readWriteDelegate.isWriteLocked();
  }

  /**
   * Queries if the write lock is held by the current thread.
   *
   * @return {@code true} if the current thread holds the write lock and {@code false} otherwise
   */
  public boolean isWriteLockedByCurrentThread() {
    return readWriteDelegate.isWriteLockedByCurrentThread();
  }

  /**
   * Queries the number of reentrant write holds on this lock by the current thread. A writer thread
   * has a hold on a lock for each lock action that is not matched by an unlock action.
   *
   * @return the number of holds on the write lock by the current thread, or zero if the write lock
   *     is not held by the current thread
   */
  public int getWriteHoldCount() {
    return readWriteDelegate.getWriteHoldCount();
  }

  /**
   * Queries if the reserve lock is held by any thread. This method is designed for use in
   * monitoring system state, not for synchronisation control.
   *
   * @return {@code true} if any thread holds the reserve lock and {@code false} otherwise
   */
  public boolean isReserveLocked() {
    return reserveDelegate.isLocked();
  }

  /**
   * Queries if the reserve lock is held by the current thread.
   *
   * @return {@code true} if the current thread holds the reserve lock and {@code false} otherwise
   */
  public boolean isReserveLockedByCurrentThread() {
    return reserveDelegate.isHeldByCurrentThread();
  }

  /**
   * Queries the number of reentrant reserve holds on this lock by the current thread. A reserve
   * thread has a hold on a lock for each lock action that is not matched by an unlock action.
   *
   * @return the number of holds on the reserve lock by the current thread, or zero if the reserve
   *     lock is not held by the current thread
   */
  public int getReserveHoldCount() {
    return reserveDelegate.getHoldCount();
  }

  /**
   * Queries the number of reentrant read holds on this lock by the current thread. A reader thread
   * has a hold on a lock for each lock action that is not matched by an unlock action.
   *
   * @return the number of holds on the read lock by the current thread, or zero if the read lock is
   *     not held by the current thread
   */
  public int getReadHoldCount() {
    return readWriteDelegate.getReadHoldCount();
  }
}
