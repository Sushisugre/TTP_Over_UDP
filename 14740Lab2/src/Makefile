make:   FTPClient.class \
        FTPServer.class \
        Datagram.class \
        FTPData.class \
        FTPMeta.class \
        TTPSegment.class \
        DatagramService.class \
        DataUtil.class \
        TTPConnection.class \
        TTPService.class

FTPClient.class:    applications/FTPClient.java
	javac $<
FTPServer.class:    applications/FTPServer.java
	javac $<

Datagram.class:    datatypes/Datagram.java
	javac $<
FTPData.class:    datatypes/FTPData.java
	javac $<
FTPMeta.class:    datatypes/FTPMeta.java
	javac $<
TTPSegment.class:    datatypes/TTPSegment.java
	javac $<

DatagramService.class:    services/DatagramService.java
	javac $<
DataUtil.class:    services/DataUtil.java
	javac $<
TTPConnection.class:    services/TTPConnection.java
	javac $<
TTPService.class:    services/TTPService.java
	javac $<

%.class: %.java
	javac $<

start_server:
	java applications.FTPServer 4096 10 15000

start_client1:
	java applications.FTPClient 2048 5 15000 big_file.txt

start_client2:
	java applications.FTPClient 2049 5 15000 small_file.txt



clean:
	rm -f *.class



