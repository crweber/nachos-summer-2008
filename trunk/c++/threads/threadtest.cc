// threadtest.cc 
//	Simple test case for the threads assignment.
//
//	Create two threads, and have them context switch
//	back and forth between themselves by calling Thread::Yield, 
//	to illustratethe inner workings of the thread system.
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// All rights reserved.  See copyright.h for copyright notice and limitation 
// of liability and disclaimer of warranty provisions.

#include "copyright.h"
#include "system.h"
#include "synch.h"
//----------------------------------------------------------------------
// SimpleThread
// 	Loop 5 times, yielding the CPU to another ready thread 
//	each iteration.
//
//	"which" is simply a number identifying the thread, for debugging
//	purposes.
//----------------------------------------------------------------------

void
SimpleThread(int which)
{
    Lock *lock = new Lock("myLock");
    lock->Acquire();    
    int num;
    
    for (num = 0; num < 5; num++) {
	printf("*** thread %d looped %d times\n", which, num);
	
        currentThread->Yield();
    }

    lock->Release();

}

// all threads should see the same variable to modify
// and the same lock
int someInt = 0;
Lock* lock = new Lock("myLock");
void LockingTest(int which)
{
    // acquire the lock
    lock->Acquire();

    // check if we should modify the variable
    if (someInt == 0) {
        // if somehow a thread loses control between checking that someInt is 0
        // and incrementing someInt, then someInt would have a value different
        // to zero in the end!!!
        DEBUG('t', "Thread #%d is about to yield\n", which);
        currentThread->Yield();
        DEBUG('t', "Thread %d was the one modifying someInt\n", which);
        someInt++;
    }

    // release the lock
    lock->Release();
    
    // check that it all went fine
    ASSERT(someInt == 1);
}

//----------------------------------------------------------------------
// ThreadTest
// 	Set up a ping-pong between two threads, by forking a thread 
//	to call SimpleThread, and then calling SimpleThread ourselves.
//----------------------------------------------------------------------

void
ThreadTest()
{
    DEBUG('t', "Entering SimpleTest");
    int numThreads = 5;
    Thread * setOfThreads[numThreads];
    
    for (int i = 0; i < numThreads; i++)
    {
        setOfThreads[i] = new Thread("ThreadTest"); 
    }
//    Thread *t = new Thread("forked thread");
 
  //  Thread *t1 = new Thread ("Ursu thread");
    for (int i = 0; i < numThreads; i++){
       Thread* t = (Thread*)setOfThreads[i];
       t->Fork(LockingTest, i);
    }
    
//    SimpleThread(0);
}

