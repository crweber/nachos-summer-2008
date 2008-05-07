// synch.cc 
//	Routines for synchronizing threads.  Three kinds of
//	synchronization routines are defined here: semaphores, locks 
//   	and condition variables (the implementation of the last two
//	are left to the reader).
//
// Any implementation of a synchronization routine needs some
// primitive atomic operation.  We assume Nachos is running on
// a uniprocessor, and thus atomicity can be provided by
// turning off interrupts.  While interrupts are disabled, no
// context switch can occur, and thus the current thread is guaranteed
// to hold the CPU throughout, until interrupts are reenabled.
//
// Because some of these routines might be called with interrupts
// already disabled (Semaphore::V for one), instead of turning
// on interrupts at the end of the atomic operation, we always simply
// re-set the interrupt state back to its original value (whether
// that be disabled or enabled).
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// All rights reserved.  See copyright.h for copyright notice and limitation 
// of liability and disclaimer of warranty provisions.

#include "copyright.h"
#include "synch.h"
#include "system.h"
#include "utility.h"

//----------------------------------------------------------------------
// Semaphore::Semaphore
// 	Initialize a semaphore, so that it can be used for synchronization.
//
//	"debugName" is an arbitrary name, useful for debugging.
//	"initialValue" is the initial value of the semaphore.
//----------------------------------------------------------------------

Semaphore::Semaphore(char* debugName, int initialValue)
{
    name = debugName;
    value = initialValue;
    queue = new List;
}

//----------------------------------------------------------------------
// Semaphore::Semaphore
// 	De-allocate semaphore, when no longer needed.  Assume no one
//	is still waiting on the semaphore!
//----------------------------------------------------------------------

Semaphore::~Semaphore()
{
    delete queue;
}

//----------------------------------------------------------------------
// Semaphore::P
// 	Wait until semaphore value > 0, then decrement.  Checking the
//	value and decrementing must be done atomically, so we
//	need to disable interrupts before checking the value.
//
//	Note that Thread::Sleep assumes that interrupts are disabled
//	when it is called.
//----------------------------------------------------------------------

void
Semaphore::P()
{
    IntStatus oldLevel = interrupt->SetLevel(IntOff);	// disable interrupts
    
    while (value == 0) { 			// semaphore not available
	queue->Append((void *)currentThread);	// so go to sleep
	currentThread->Sleep();
    } 
    value--; 					// semaphore available, 
						// consume its value
    
    (void) interrupt->SetLevel(oldLevel);	// re-enable interrupts
}

//----------------------------------------------------------------------
// Semaphore::V
// 	Increment semaphore value, waking up a waiter if necessary.
//	As with P(), this operation must be atomic, so we need to disable
//	interrupts.  Scheduler::ReadyToRun() assumes that threads
//	are disabled when it is called.
//----------------------------------------------------------------------

void
Semaphore::V()
{
    Thread *thread;
    IntStatus oldLevel = interrupt->SetLevel(IntOff);

    thread = (Thread *)queue->Remove();
    if (thread != NULL)	   // make thread ready, consuming the V immediately
	scheduler->ReadyToRun(thread);
    value++;
    (void) interrupt->SetLevel(oldLevel);
}

// Dummy functions -- so we can compile our later assignments 
// Note -- without a correct implementation of Condition::Wait(), 
// the test case in the network assignment won't work!
//
// For locking we use a Semaphore. We also keep the owner thread for information.
Lock::Lock(char* debugName) {
	name = debugName;
	sem = new Semaphore(debugName, 1);
	ownerThread = NULL;
}
//delete the allocated Semaphore
Lock::~Lock() {
   delete sem;
}

//Acquire the lock for the current thread
void Lock::Acquire() {
	DEBUG('t', "\nTry to acquire lock %s for thread %s\n",name, currentThread->getName());
	sem->P();
	ownerThread = currentThread;
	DEBUG('t', "\nAcquired lock %s for thread %s\n", name, currentThread->getName());
      
}

//Release the lock held by current thread
void Lock::Release()
{
	ASSERT(currentThread == ownerThread);
	DEBUG('t', "Thread %s tries to release lock %s\n",currentThread->getName(), name);
        ownerThread = NULL;
	sem->V();
	DEBUG('t', "Thread %s released lock %s\n",currentThread->getName(), name);
}

//Test if current thread is holding the lock
bool Lock::isHeldByCurrentThread()
{
	return (ownerThread == currentThread);
}

Condition::Condition(char* debugName) 
{
	name = debugName;
	waitingThreads = new List;
}
Condition::~Condition() 
{
       delete waitingThreads;	
}

//wait for the condition to be signaled(broadcasted)
void Condition::Wait(Lock* conditionLock) { 
	
	ASSERT(conditionLock->isHeldByCurrentThread());
	DEBUG('t', "Thread %s waiting on condition variable %s\n", currentThread->getName(), name);
	conditionLock->Release();
	IntStatus oldLevel = interrupt->SetLevel(IntOff);// disable interrupts
        waitingThreads->Append((void*)currentThread);
        currentThread->Sleep();
        (void)interrupt->SetLevel(oldLevel);	// re-enable interrupts
        DEBUG('t', "Trying to reacquire condition %s's lock (%s) for thread %s\n", name, conditionLock->getName(),currentThread->getName());
        conditionLock->Acquire();
	DEBUG('t', "Reacquired condition %s's lock (%s) for thread %s\n", name, conditionLock->getName(),currentThread->getName());
}

//Signal a single waiting thread to wake up (which waiting thread wakes up is undetermined)
void Condition::Signal(Lock* conditionLock) 
{ 
	ASSERT(conditionLock->isHeldByCurrentThread());
        DEBUG('t', "Signalling condition %s\n", name);
        IntStatus oldLevel = interrupt->SetLevel(IntOff);// disable interrupts
        Thread* threadToRun = (Thread*)waitingThreads->Remove();
        if (threadToRun != NULL){
		DEBUG('t', "Preparing to wake up thread %s\n", threadToRun->getName());
		scheduler->ReadyToRun(threadToRun);
	}
        (void)interrupt->SetLevel(oldLevel);  // re-enable interrupts
}
//Broadcast a wake up signal to all waiting threads
void Condition::Broadcast(Lock* conditionLock) 
{
        ASSERT(conditionLock->isHeldByCurrentThread());
        DEBUG('t', "Signalling condition %s\n", name);
        IntStatus oldLevel = interrupt->SetLevel(IntOff);// disable interrupts
        Thread* threadToRun = (Thread*)waitingThreads->Remove();
        while (threadToRun != NULL){
	     DEBUG('t', "Preparing to wake up thread %s\n", threadToRun->getName());
	     scheduler->ReadyToRun(threadToRun);
	     threadToRun = (Thread*)waitingThreads->Remove();
        }
        (void)interrupt->SetLevel(oldLevel);  // re-enable interrupts

}
