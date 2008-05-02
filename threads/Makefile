JC = javac -classpath .:../machine:../filesys:../userprog:../threads:../vm

all:
	${JC} ../threads/Nachos.java

cleanclass:
	(find .. -name "*.class" -print | xargs rm)

cleanbackup:
	(find .. -name "*~" -print | xargs rm)
