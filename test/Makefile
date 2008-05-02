# use normal make for this Makefile
#
# Makefile for building user programs to run on top of Nachos
#
# Several things to be aware of:
#
#    Nachos assumes that the location of the program startup routine (the
# 	location the kernel jumps to when the program initially starts up)
#       is at location 0.  This means: start.o must be the first .o passed 
# 	to ld, in order for the routine "Start" to be loaded at location 0
#

.SUFFIXES:

NACHOSDIR=..

# if you are cross-compiling, you need to point to the right executables
# and change the flags to ld and the build procedure for as
GCCDIR = /home/comp421/decstation-ultrix/bin/
LDFLAGS = -T script -N
ASFLAGS = -mips1
CPPFLAGS = $(INCDIR)


# if you aren't cross-compiling:
#GCCDIR =
#LDFLAGS = -N -T 0
#ASFLAGS =
#CPPFLAGS = -P $(INCDIR)


CC = $(GCCDIR)gcc
AS = $(GCCDIR)as
LD = $(GCCDIR)ld

CPP = /usr/ccs/lib/cpp
#CPP = /usr/lang/cpp  ### FOR Sun4 machines
INCDIR =-I$(NACHOSDIR)/userprog -I$(NACHOSDIR)/threads
CFLAGS = -G 0 $(INCDIR) -B/home/comp421/lib/gcc-lib/ -B/home/comp421/decstation-ultrix/bin/ -c
SFLAGS = -G 0 $(INCDIR) -B/home/comp421/lib/gcc-lib/ -S

start.o: start.s $(NACHOSDIR)/userprog/syscall.h
	$(CPP) $(CPPFLAGS) start.s > strt.s
	$(AS) $(ASFLAGS) -o start.o strt.s
	rm strt.s

%.s: %.c
	$(CC) $(SFLAGS) $< 
%.o: %.c
	$(CC) $(CFLAGS) $<
%: %.o start.o
	$(LD) $(LDFLAGS) start.o $< -o $*.coff
	rm $*.o
	$(NACHOSDIR)/bin/coff2noff $*.coff $@
	rm $*.coff
