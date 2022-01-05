package reserve.lock;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Extends {@link ReadWriteLock} with a third lock used to reserve the write lock. Only holders of
 * the reservation lock can acquire the write lock any number of readers can hold the read lock
 * while only a single thread can hold the reservation lock. The holder of the reservation lock MUST
 * only perform read operations and must upgrade to the write lock before performing any write
 * operation.
 *
 * <p>Note, that a user of the lock does not need access to the reservation lock inorder to get a
 * hold on the write lock. Any call to acquire the write lock without first holding the reservation
 * lock will be queued against the reservation lock. Once the reservation lock is held by the
 * calling thread the write lock is acquired first waiting for all readers to complete as usual.
 *
 * <p>An implementation {@link ReserveWriteReadWriteLock} can be cast to {@link ReadWriteLock} where
 * it is desirable not to expose the reservation lock.
 *
 * @author omilton
 * @see ReadWriteLock
 */
public interface ReserveWriteReadWriteLock extends ReadWriteLock {

  /**
   * Returns the lock used for reserving the write lock.
   *
   * @return the lock used for reserving the write lock.
   */
  Lock reserveLock();
}
