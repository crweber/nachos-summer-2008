// ThreadTest.java
//	Simple test class for the threads assignment.
//
//	Create two threads, and have them context switch
//	back and forth between themselves by calling yield(), 
//	to illustratethe thread system.
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// All rights reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.


//----------------------------------------------------------------------
// SimpleRunnable object
// 	Loop 5 times, yielding the CPU to another ready thread 
//	each iteration.
//
//	"which" is simply a number identifying the thread, for debugging
//	purposes.
//----------------------------------------------------------------------

class SimpleRunnable implements Runnable {
  private int which;
    
  public SimpleRunnable(int w) {
    which = w;
  }

  public void run() {
    for (int num = 0; num < 5; num++) {
	System.out.print("*** thread " + which + " looped " + num + " times\n");
        //Thread.currentThread().yield();
	NachosThread.thisThread().Yield();
    }
  }

}


class AlarmObject implements Runnable {
  private int which;
    
  public AlarmObject(int w) {
    which = w;
  }

  public void run() {
    System.out.println("Alarm " + which);
  }

}


//----------------------------------------------------------------------
// ThreadTest object
// 	Set up a ping-pong between two threads, by forking a thread 
//	to execute a SimpleRunnable object, and then calling 
//      SimpleRunnable.run() on another instance ourselves.
//----------------------------------------------------------------------

class ThreadTest {
  private static NachosThread th1,th2;
  private static Timer t1,t2,t3;
  private static SimpleRunnable o1, o2;
  
  public static void start() {

    Debug.println('t', "Entering SimpleTest");

    th1 = new NachosThread("Testthread 1");
    th2 = new NachosThread("Testthread 2");
    o1 = new SimpleRunnable(1);
    o2 = new SimpleRunnable(2);

    t1 = new Timer(new AlarmObject(1), false, true);
    t2 = new Timer(new AlarmObject(2), false, true);
    t3 = new Timer(new AlarmObject(3), false, true);

    th1.fork(o1);
    th2.fork(o2);
    //o2.run();
    
    t1.cancel();
    t2.cancel();
    t3.cancel();

  }
}
