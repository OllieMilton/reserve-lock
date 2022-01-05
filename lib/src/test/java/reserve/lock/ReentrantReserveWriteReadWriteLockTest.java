package reserve.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import org.junit.jupiter.api.Test;

class ReentrantReserveWriteReadWriteLockTest {

  @Test
  void cannotAcquireWriteLockWhileHoldingReadLock() {
    ReadWriteLock lock = new ReentrantReserveWriteReadWriteLock(true);
    lock.readLock().lock();
    Lock writeLock = lock.writeLock();
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> writeLock.lock());
    assertEquals("Cannot acquire write lock while holding the read lock.", e.getMessage());

    e = assertThrows(IllegalStateException.class, () -> writeLock.lockInterruptibly());
    assertEquals("Cannot acquire write lock while holding the read lock.", e.getMessage());

    e = assertThrows(IllegalStateException.class, () -> writeLock.tryLock());
    assertEquals("Cannot acquire write lock while holding the read lock.", e.getMessage());

    e =
        assertThrows(
            IllegalStateException.class, () -> writeLock.tryLock(1, TimeUnit.MILLISECONDS));
    assertEquals("Cannot acquire write lock while holding the read lock.", e.getMessage());
  }

  @Test
  void cannotAcquireReserveLockWhileHoldingReadLock() {
    ReserveWriteReadWriteLock lock = new ReentrantReserveWriteReadWriteLock(true);
    lock.readLock().lock();
    Lock reserveWrite = lock.reserveLock();
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> reserveWrite.lock());
    assertEquals("Cannot acquire reserve write lock while holding the read lock.", e.getMessage());

    e = assertThrows(IllegalStateException.class, () -> reserveWrite.lockInterruptibly());
    assertEquals("Cannot acquire reserve write lock while holding the read lock.", e.getMessage());

    e = assertThrows(IllegalStateException.class, () -> reserveWrite.tryLock());
    assertEquals("Cannot acquire reserve write lock while holding the read lock.", e.getMessage());

    e =
        assertThrows(
            IllegalStateException.class, () -> reserveWrite.tryLock(1, TimeUnit.MILLISECONDS));
    assertEquals("Cannot acquire reserve write lock while holding the read lock.", e.getMessage());
  }

  @Test
  void cannotReleaseReserveLockWhileHoldingWriteLock() {
    ReserveWriteReadWriteLock lock = new ReentrantReserveWriteReadWriteLock(true);
    Lock reserveLock = lock.reserveLock();
    reserveLock.lock();
    lock.writeLock().lock();
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> reserveLock.unlock());
    assertEquals(
        "Cannot release the reserve write lock while holding the write lock.", e.getMessage());
  }

  @Test
  void writeLockAcquiresReserveLock() {
    ReentrantReserveWriteReadWriteLock lock = new ReentrantReserveWriteReadWriteLock(true);
    lock.writeLock().lock();
    assertEquals(1, lock.getReserveHoldCount());
    assertEquals(1, lock.getWriteHoldCount());
    lock.writeLock().unlock();
    assertEquals(0, lock.getReserveHoldCount());
    assertEquals(0, lock.getWriteHoldCount());
  }

  @Test
  void canDownGradeToReserve() {
    ReentrantReserveWriteReadWriteLock lock = new ReentrantReserveWriteReadWriteLock(true);
    Lock reserveLock = lock.reserveLock();
    reserveLock.lock();
    lock.writeLock().lock();
    assertEquals(2, lock.getReserveHoldCount());
    assertEquals(1, lock.getWriteHoldCount());
    lock.writeLock().unlock();
    assertEquals(1, lock.getReserveHoldCount());
    assertEquals(0, lock.getWriteHoldCount());
    reserveLock.unlock();
    assertEquals(0, lock.getReserveHoldCount());
    assertEquals(0, lock.getWriteHoldCount());
  }

  @Test
  void canDownGradeToRead() {
    ReentrantReserveWriteReadWriteLock lock = new ReentrantReserveWriteReadWriteLock(true);
    lock.writeLock().lock();
    assertEquals(1, lock.getReserveHoldCount());
    assertEquals(1, lock.getWriteHoldCount());
    lock.readLock().lock();
    assertEquals(1, lock.getReserveHoldCount());
    assertEquals(1, lock.getWriteHoldCount());
    assertEquals(1, lock.getReadHoldCount());
    lock.writeLock().unlock();
    assertEquals(0, lock.getReserveHoldCount());
    assertEquals(0, lock.getWriteHoldCount());
    assertEquals(1, lock.getReadHoldCount());
  }

  @Test
  void readersGetAccessWhileReserveIsHeld() throws InterruptedException {
    ReentrantReserveWriteReadWriteLock lock = new ReentrantReserveWriteReadWriteLock(true);
    // get the reservation lock
    lock.reserveLock().lock();
    AtomicInteger readCount = new AtomicInteger(0);
    CountDownLatch readLatch = new CountDownLatch(2);
    new Thread(
            () -> {
              lock.readLock().lock();
              readCount.addAndGet(lock.getReadHoldCount());
              readLatch.countDown();
            })
        .start();
    new Thread(
            () -> {
              lock.readLock().lock();
              readCount.addAndGet(lock.getReadHoldCount());
              readLatch.countDown();
            })
        .start();
    // wait for the other two threads to get the read lock.
    readLatch.await();
    assertEquals(2, readCount.get());
    assertEquals(1, lock.getReserveHoldCount());
    // prove reservation lock is reentrant
    lock.reserveLock().lock();
    assertEquals(2, lock.getReserveHoldCount());
  }

  @Test
  void reserveLockMakesWritersQueue() throws InterruptedException {
    ReentrantReserveWriteReadWriteLock lock = new ReentrantReserveWriteReadWriteLock(true);
    CountDownLatch writeLatch = new CountDownLatch(1);
    // get the reservation lock...
    lock.reserveLock().lock();
    new Thread(
            () -> {
              // this thread wants the write lock but another has the reservation lock
              // block until the reservation lock has been released.
              lock.writeLock().lock();
              writeLatch.countDown();
            })
        .start();
    assertEquals(1, lock.getReserveHoldCount());
    assertFalse(lock.isWriteLocked());
    // release the reserve lock...
    lock.reserveLock().unlock();
    // block until the other thread has the write lock.
    writeLatch.await();
    // this thread no longer holds the reservation lock
    assertEquals(0, lock.getReserveHoldCount());
    // other thread has both the write lock and reservation lock.
    assertTrue(lock.isWriteLocked());
    assertTrue(lock.isReserveLocked());
  }

  @Test
  void canUpgradeToWriter() throws InterruptedException {
    ReentrantReserveWriteReadWriteLock lock = new ReentrantReserveWriteReadWriteLock(true);
    // get the read lock...
    lock.readLock().lock();
    CountDownLatch writeLatch = new CountDownLatch(1);
    new Thread(
            () -> {
              // get the reservation lock - this is allowed while another thread has the read lock.
              lock.reserveLock().lock();
              // this is where reads can be performed along with the reader threads.
              // once the write lock is required the call to get the write lock blocks until the
              // read lock has been released.
              lock.writeLock().lock();
              writeLatch.countDown();
            })
        .start();
    assertEquals(1, lock.getReadHoldCount());
    assertTrue(lock.isReserveLocked());
    assertFalse(lock.isWriteLocked());
    // release the read lock and wait for the other thread to get the write lock.
    lock.readLock().unlock();
    writeLatch.await();
    // this thread no longer holds the reservation lock
    assertEquals(0, lock.getReadHoldCount());
    // other thread has both the write lock and reservation lock.
    assertTrue(lock.isReserveLocked());
    assertTrue(lock.isWriteLocked());
  }
}
