

#### Folder Structure

applications/
    Application executables
    - FTPClient
    - FTPServer

datatypes/
    Classes that encapsulate data when transmitting, listed in a top-down order
    - Application level
        - FTPMeta:      Meta data for the requested file, including size, MD5 etc.
        - FTPData:      Contains a chunk of the requested file
    - TTP level
        - TTPSegment
    - UDP
        - Datagram

services/
    Implementation of the transportation protocol
    - DatagramServices
    - DataUtil:         util that handles serialization & deserialization, checksum computation, etc
    - TTPConnection:    simulate a socket between 2 host, handle Go-Back-N window, timer, etc
    - TTPServices:      core of TTP implementation, receive and send packet, etc


#### Execution

    make clean
    make                # Compile
    make start_server   # Start server on port 4096
    make start_client1  # Start client 1 on port 2048, which requests a large 10MB file
    make start_client2  # Start client 2 on port 2049, which requests a small file

                        # Or after make, execute following commands and provide proper argument:
                        #
                        # java applications.FTPServer 4096 10 15000
                        # java applications.FTPClient 2048 5 15000 10m.txt
                        # java applications.FTPClient 2049 5 15000 small_file.txt

                        # The document that client received will have a _copy suffix
                        # In client, it makes assumption that server is started as
